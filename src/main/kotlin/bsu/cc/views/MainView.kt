package bsu.cc.views

import bsu.cc.ConfigurationKeys
import bsu.cc.Styles
import bsu.cc.parser.identifyAndWriteConflicts
import javafx.scene.control.TextField
import javafx.scene.input.TransferMode
import javafx.scene.layout.Priority
import javafx.stage.FileChooser
import tornadofx.*
import java.awt.Desktop
import java.io.File
import kotlin.String

class MainView : View("Conflict Checker") {
    var fileNameField: TextField by singleAssign()

    val constraintsPicker = FileDropDownFragment("Constraints: ",
            """..\..\..\src\main\resources\""" ) { path ->
        with(config) {
            if (path != null) {
                set(ConfigurationKeys.CONSTRAINT_PATH_KEY to path.toAbsolutePath().toString())
                save()
            }
        }
    }

    init {
        with (config) {
            //defaults
            val path = string(ConfigurationKeys.CONSTRAINT_PATH_KEY)
            if (path == null) {
                set(ConfigurationKeys.CONSTRAINT_PATH_KEY to """..\..\..\src\main\resources\conflicts.csv""")
            }

            val dir = string(ConfigurationKeys.CONSTRAINT_DIR_KEY)
            if (dir == null) {
                set(ConfigurationKeys.CONSTRAINT_DIR_KEY to """..\..\..\src\main\resources\""")
            }
            save()
        }

        constraintsPicker.setSelected(config.string(ConfigurationKeys.CONSTRAINT_PATH_KEY))
        constraintsPicker.dir = config.string(ConfigurationKeys.CONSTRAINT_DIR_KEY)
    }

    override val root = borderpane {
        setOnDragOver { event ->
            val dragBoard = event.dragboard
            if (dragBoard.hasFiles()) {
                event.acceptTransferModes(TransferMode.LINK)
            } else {
                event.consume()
            }
        }
        setOnDragDropped {  event ->
            val dragBoard = event.dragboard
            var success = false

            if (dragBoard.hasFiles()) {
                success = true
                val file = dragBoard.files[0]
                fileNameField.text = file.absolutePath
            }

            event.isDropCompleted = success
            event.consume()
        }
        addClass(Styles.welcomeScreen)
        top {
            borderpane {
                top {
                    menubar {
                        menu("File") {
                            item("Choose Constraints Directory").action {
                                val dir = chooseDirectory()
                                if(dir != null && dir.isDirectory) {
                                    with(config) {
                                        set(ConfigurationKeys.CONSTRAINT_DIR_KEY to dir.absolutePath)
                                        save()
                                    }
                                    constraintsPicker.dir = dir.absolutePath.toString()
                                }
                            }
                            item("Export", "Shortcut+E").action {
                                println("Constraint file path is ${config[ConfigurationKeys.CONSTRAINT_PATH_KEY]}")
                                println("Exporting! (TO BE IMPLEMENTED)")
                            }
                        }
                        menu("Edit") {
                            item("Copy", "Shortcut+C").action {
                                println("Copying!")
                            }
                            item("Paste", "Shortcut+V").action {
                                println("Pasting!")
                            }
                        }
                        menu("View") {
                            item("Theme").action {
                                println("Theme needs to be implemented")
                            }
                        }
                    }
                }
                bottom {
                    label(title).addClass(Styles.heading)
                }
            }
        }
        center {
            vbox {
                addClass(Styles.content)
                add(constraintsPicker)
                borderpane {
                    left {
                        button("Choose File") {
                            setOnAction {
                                val file = FileChooser().showOpenDialog(null)
                                if (file != null) {
                                    fileNameField.text = file.absolutePath
                                }
                            }
                        }
                    }

                    center {
                        fileNameField = textfield("""..\..\..\src\main\resources\Spring 2019 Validation Report Example.xlsx""")
                    }
                }
            }
        }
        bottom {
            hbox {
                addClass(Styles.footer)
                region {
                    hgrow = Priority.ALWAYS
                }
                button("Process") {
                    setOnAction {
                        showConflicts(fileNameField.text)
                    }
                }
            }
        }
    }

    fun showConflicts(fileName : String) {
        val outputFile = identifyAndWriteConflicts(fileName,
                config.getProperty(ConfigurationKeys.CONSTRAINT_PATH_KEY))
        Desktop.getDesktop().open(File(outputFile))
    }
}