package bsu.cc.parser

import bsu.cc.ConfigurationKeys
import bsu.cc.constraints.ClassConstraint
import bsu.cc.constraints.readConstraintFile
import bsu.cc.schedule.*
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFFont
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import tornadofx.ConfigProperties
import java.io.File
import java.io.FileOutputStream
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.text.SimpleDateFormat
import java.util.*
import javax.security.auth.login.Configuration

const val MEETING_DATES_CELL_INDEX = 16
const val REPORT_NAME = "ConflictReport"
const val DATE_FORMAT_STRING = "yyyy.MM.dd"

enum class ConflictType {
    INSTRUCTOR, ROOM, CONSTRAINT
}

val ConflictColumnMap = mapOf<ConflictType, Short>(
        Pair(ConflictType.INSTRUCTOR, 7),
        Pair(ConflictType.ROOM, 6),
        Pair(ConflictType.CONSTRAINT, 2)
)
val ConflictColorMap = mapOf(
        Pair(ConflictType.INSTRUCTOR, IndexedColors.LIGHT_BLUE),
        Pair(ConflictType.ROOM, IndexedColors.LIGHT_ORANGE),
        Pair(ConflictType.CONSTRAINT, IndexedColors.LIGHT_GREEN)
)

fun displayConflictsOnNewSheet(workbook: XSSFWorkbook, classSchedules: List<ClassSchedule>, constraints: List<ClassConstraint>): XSSFWorkbook {
    val instructorConflicts = checkInstructors(classSchedules, constraints).mapKeys { "${it.key.lastName}, ${it.key.firstName}" }
    val roomConflicts = checkRooms(classSchedules, constraints)
    val constraintConflicts = checkConstraints(classSchedules, constraints).mapKeys { it.key.classes.joinToString() }

    val conflictsSheet = workbook.createSheet("Conflicts")

    fun headerStyle(conflictType: ConflictType): XSSFCellStyle {
        return createHeaderStyle(workbook, conflictType)
    }

    var rowIndex = 0
    rowIndex = addConflicts(conflictsSheet, rowIndex, "Instructor Conflicts", headerStyle(ConflictType.INSTRUCTOR), instructorConflicts)
    rowIndex = addConflicts(conflictsSheet, rowIndex, "Room Conflicts", headerStyle(ConflictType.ROOM), roomConflicts)
    addConflicts(conflictsSheet, rowIndex, "Constraint Conflicts", headerStyle(ConflictType.CONSTRAINT), constraintConflicts)

    0.rangeTo(ClassSchedule.xlsxHeaders.size + 3).forEach { colIndex ->
        conflictsSheet.autoSizeColumn(colIndex)
    }

    return workbook
}

internal fun createHeaderStyle(workbook: XSSFWorkbook, conflictType: ConflictType): XSSFCellStyle {
    val headerStyle = workbook.createCellStyle()
    val headerFont = workbook.createFont()
    headerFont.fontName = "Arial"
    headerFont.fontHeightInPoints = 24
    headerStyle.setFont(headerFont)
    headerStyle.fillPattern = FillPatternType.SOLID_FOREGROUND
    headerStyle.fillForegroundColor = (ConflictColorMap[conflictType]?:IndexedColors.RED).index
    return headerStyle
}

fun addConflicts(sheet: XSSFSheet, startIndex: Int, headerName: String, headerStyle: XSSFCellStyle, conflicts: Map<String, Set<List<ClassSchedule>>>): Int {
    var index = startIndex + 1 //One row of padding
    val header = sheet.createRow(index++)
    val headerCell = header.createCell(0)
    headerCell.setCellValue(headerName)
    headerCell.cellStyle = headerStyle
    header.rowStyle = headerStyle
    val colNames = sheet.createRow(index++)
    ClassSchedule.xlsxHeaders.withIndex().forEach{ (index, header) ->
        colNames.createCell(index + 2).setCellValue(header)
    }

    conflicts.filterValues { it.isNotEmpty() }.keys.forEach{ key ->
        val constraintRow = sheet.createRow(index++)
        constraintRow.createCell(0).setCellValue(key)
        var conflictIndex = 1
        (conflicts[key]?: throw IllegalStateException("Key does not have value")).forEach { classSchedules ->
            val conflictRow = sheet.createRow(index++)
            conflictRow.createCell(1).setCellValue("Conflict ${conflictIndex++}")
            classSchedules.forEach { classSchedule ->
                classScheduleToRow(classSchedule, sheet, index++, 2)
            }
        }
    }
    return index
}


fun highlightConflictsOnNewSheet(workbook: XSSFWorkbook, classSchedules: List<ClassSchedule>, constraints: List<ClassConstraint>): XSSFWorkbook {
    val conflicts = listOf(
        checkInstructors(classSchedules, constraints).map { Pair(ConflictType.INSTRUCTOR, it) },
        checkRooms(classSchedules, constraints).map { Pair(ConflictType.ROOM, it) },
        checkConstraints(classSchedules, constraints).map { Pair(ConflictType.CONSTRAINT, it) }
    ).flatten()

    val highlightSheet = workbook.createSheet("Highlighted Schedule")
    val headerRow = highlightSheet.createRow(0)
    ClassSchedule.xlsxHeaders.withIndex().forEach{ (index, header) ->
        headerRow.createCell(index).setCellValue(header)
    }

    classSchedules.withIndex().forEach { (index, classSchedule) ->
        val rowIndex = index + 1
        classScheduleToRow(classSchedule, highlightSheet, rowIndex)
        val violatedConstraints = conflicts.filter { (_, conflict) -> conflict.value.flatten().contains(classSchedule) }
        violatedConstraints.map{ it.first }.forEach { conflictType ->
            val color = ConflictColorMap[conflictType]?: IndexedColors.RED
            highlightRow(highlightSheet, rowIndex, color, colIndex = ConflictColumnMap[conflictType])
        }
    }
    headerRow.firstCellNum.rangeTo(headerRow.lastCellNum).forEach { colIndex ->
        highlightSheet.autoSizeColumn(colIndex)
    }

    return workbook
}

fun writeWorkbook(workbook: XSSFWorkbook, fileName: String) {
    FileOutputStream(fileName).use {
        workbook.write(it)
    }
}

data class ConflictStats(
        val outputFileName: String,
        val numConstraintConflicts: Int,
        val numRoomConflicts: Int,
        val numInstructorConflicts: Int
)

fun identifyAndWriteConflicts(fileName: String, constraintsFileName: String, config: ConfigProperties, sheetIndex: Int = 0) : String {
    val workbook = readWorkbook(fileName)
    val scheduleSheet = workbook.getSheetAt(sheetIndex) ?: throw IllegalArgumentException("No sheet present at given index")
    val constraints = readConstraintFile(File(constraintsFileName))

    val classSchedules = sheetToDataClasses(
            sheet = scheduleSheet,
            dataProducer = ::classScheduleProducer,
            rowFilter = ::incompleteRowFilter,
            ignoreDuplicateHeaders = true
    ).toList()

    val highlightedWB = highlightConflictsOnNewSheet(workbook, classSchedules, constraints)
    val finalWB = displayConflictsOnNewSheet(highlightedWB, classSchedules, constraints)
    val newFileName = getNewFileName(config)
    
    writeWorkbook(finalWB,  newFileName)

    return newFileName
}

private fun getNewFileName(config: ConfigProperties): String {
    val dateString = SimpleDateFormat(DATE_FORMAT_STRING).format(Date())
    var index: Int
    with(config) {
        val lastDateString = string(ConfigurationKeys.LAST_SAVE_DATE_KEY, "")
        index = int(ConfigurationKeys.OUTPUT_FILE_INDEX_KEY, 1) ?: 1
        if(lastDateString != dateString) { index = 1 }
    }

    if(File(createFileName(dateString, index)).exists()) {
        val currentDirectory = File(".")
        val indices = currentDirectory.listFiles()
                .map{ it.name }
                .filter{ it.startsWith(dateString) && it.endsWith("$REPORT_NAME.xlsx") }
                .map{ Regex("""(?:.+?\.){3}(.+?)\.""").find(it) }
                .map{ it?.groups?.get(1)?.value?.toInt() ?: -1 }
                .sorted()

        index = IntRange(index, Int.MAX_VALUE).find {!indices.contains(it)} ?: -1
    }

    with(config) {
        set(ConfigurationKeys.OUTPUT_FILE_INDEX_KEY to index + 1)
        set(ConfigurationKeys.LAST_SAVE_DATE_KEY to dateString)
        save()
    }

    return createFileName(dateString, index)
}

private fun createFileName(dateString: String, index: Int): String {
    return "$dateString.$index.$REPORT_NAME.xlsx"
}

private fun incompleteRowFilter(row: Row): Boolean {
    return row.getCell(MEETING_DATES_CELL_INDEX)?.cellType?: CellType.BLANK != CellType.BLANK
}



