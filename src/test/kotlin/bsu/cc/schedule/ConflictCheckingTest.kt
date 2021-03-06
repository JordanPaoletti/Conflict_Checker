package bsu.cc.schedule
import bsu.cc.constraints.ConstraintPriority
import io.kotlintest.matchers.boolean.shouldBeTrue
import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec
import java.time.DayOfWeek

class ConflictCheckingTest : WordSpec() {
    init {
        "Single entity checking" should {
            val noOverlapSchedule = setOf(
                    createDummyClass(1, 10, 0, 11, 15),
                    createDummyClass(2, 11, 30, 12, 45),
                    createDummyClass(3, 1, 0, 2, 15),
                    createDummyClass(4, 3, 0, 5, 15),
                    createDummyClass(5, 5, 30, 6, 59),
                    createDummyClass(6, 7, 0, 7, 30)
            )
            val overlapingSchedule = setOf(
                    createDummyClass(1, 10, 0, 11, 15),
                    createDummyClass(2, 11, 0, 12, 45),
                    createDummyClass(3, 1, 0, 2, 15),
                    createDummyClass(4, 6, 0, 6, 15),
                    createDummyClass(5, 5, 30, 7, 0),
                    createDummyClass(6, 7, 0, 7, 30)
            )

            "detect no conflicts in valid schedule" {
                val tree = buildScheduleIntervalTree()
                tree.addAll(noOverlapSchedule)

                checkForOverlaps(noOverlapSchedule, tree).size.shouldBe(0)
            }

            "detect conflicts in invalid tree" {
                val overlap1 = listOf(
                        overlapingSchedule.elementAt(0),
                        overlapingSchedule.elementAt(1)
                )

                val overlap2 = listOf(
                        overlapingSchedule.elementAt(3),
                        overlapingSchedule.elementAt(4),
                        overlapingSchedule.elementAt(5)
                )

                val overlap3 = listOf(
                        overlapingSchedule.elementAt(3),
                        overlapingSchedule.elementAt(4)
                )

                val overlap4 = listOf(
                        overlapingSchedule.elementAt(5),
                        overlapingSchedule.elementAt(4)
                )

                val overlaps = setOf(
                        overlap1,
                        overlap2,
                        overlap3,
                        overlap4
                )

                val tree = buildScheduleIntervalTree()
                tree.addAll(overlapingSchedule)

                val collisions = checkForOverlaps(overlapingSchedule, tree)
                collisions.size.shouldBe(4)

                checkOverlapsAreEqual(collisions, overlaps).shouldBeTrue()
            }
        }

        "Class Constraint checking" should {
            val constraints = listOf(
                    createConstraint(1, ConstraintPriority.PRIORITY, "cs121", "cs221", "cs321"),
                    createConstraint(2, ConstraintPriority.PRIORITY, "ece230", "cs253"),
                    createConstraint(3, ConstraintPriority.NON_PRIORITY, "math189", "cs221"),
                    // this constraint should have no effect on output
                    createConstraint(4, ConstraintPriority.IGNORE, "cs121", "cs221", "cs321")
            )

            val classes = setOf(
                    createDummyClass("cs", "121", "1", "1:00", "1:45"),
                    createDummyClass("cs", "121", "2", "2:00", "2:45"),
                    createDummyClass("cs", "121", "3", "3:00", "3:45"),

                    createDummyClass("cs", "221", "1", "3:00", "3:45"),
                    createDummyClass("cs", "221", "2", "4:00", "4:45"),

                    createDummyClass("cs", "321", "3", "4:00", "4:45"),

                    createDummyClass("ece", "230", "1", "4:00", "4:45"),
                    createDummyClass("cs", "253", "1", "2:00", "2:45"),

                    createDummyClass("math", "189", "1", "4:00", "4:45")
            )

            val conflicts = checkConstraints(classes, constraints)

            "no conflicts should be empty" {
                conflicts.getValue(constraints[1]).isEmpty().shouldBeTrue()
            }

            "check single conflict" {
                val expected = setOf(
                        listOf(classes.elementAt(4), classes.elementAt(8))
                )

                val conflict = conflicts.getValue(constraints[2])

                checkOverlapsAreEqual(conflict, expected).shouldBeTrue()
            }

            "check several conflicts" {
                val expected = setOf(
                        listOf(classes.elementAt(2), classes.elementAt(3)),
                        listOf(classes.elementAt(4), classes.elementAt(5))
                )

                val conflict = conflicts.getValue(constraints[0])

                checkOverlapsAreEqual(conflict, expected).shouldBeTrue()
            }

            "consider days of week" {
                val classes1 = listOf(
                        createDummyClass(DayOfWeek.MONDAY, "1"),
                        createDummyClass(DayOfWeek.MONDAY, "2"),
                        createDummyClass(DayOfWeek.THURSDAY, "3"),
                        createDummyClass(DayOfWeek.THURSDAY, "4"),
                        createDummyClass(DayOfWeek.THURSDAY, "5"),
                        createDummyClass(DayOfWeek.FRIDAY, "5")
                )

                val constraints1 = listOf(
                        createConstraint(1, ConstraintPriority.PRIORITY, "ece330")
                )

                val expected = setOf(
                        listOf(classes1[0], classes1[1]),
                        listOf(classes1[2], classes1[3], classes1[4])
                )

                val conflict = checkConstraints(classes1, constraints1)[constraints1[0]]
                checkOverlapsAreEqual(conflict!!, expected).shouldBeTrue()
            }
        }

        "instructors" should {
            "not have conflicts if no overlaps" {
                val classes = listOf(
                        createDummyClass("1", "1:00", "2:00",
                                Instructor("a", "A"), Instructor("b", "B")),
                        createDummyClass("2", "1:00", "2:00",
                                Instructor("c", "C"))
                )

                checkInstructors(classes, emptyList()).isEmpty().shouldBeTrue()
            }

            "not have conflicts if ignored" {
                val classes = listOf(
                        createDummyClass("1", "1:00", "2:00",
                                Instructor("a", "A")),
                        createDummyClass("2", "1:00", "2:00",
                                Instructor("a", "A"))
                )
                val constraints = listOf(
                        createConstraint(1, ConstraintPriority.IGNORE, "cs121")
                )

                checkInstructors(classes, constraints).isEmpty().shouldBeTrue()
            }

            "have conflicts if overlaps" {
                val classes = listOf(
                        createDummyClass("1", "1:00", "2:00",
                                Instructor("a", "A"), Instructor("b", "B")),
                        createDummyClass("2", "1:00", "2:00",
                                Instructor("a", "A")),
                        createDummyClass("3", "1:00", "2:00",
                                Instructor("b", "B"))
                )

                val expected1 = setOf(
                        listOf(classes[0], classes[1])
                )
                val expected2 = setOf(
                        listOf(classes[0], classes[2])
                )

                val conflicts = checkInstructors(classes, emptyList())
                conflicts.size.shouldBe(2)
                checkOverlapsAreEqual(conflicts.getValue(Instructor("a", "A")), expected1).shouldBeTrue()
                checkOverlapsAreEqual(conflicts.getValue(Instructor("b", "B")), expected2).shouldBeTrue()
            }

            "ignore STAFF, STAFF" {
                val classes = listOf(
                        createDummyClass("1", "1:00", "2:00",
                                Instructor("STAFF", "STAFF")),
                        createDummyClass("2", "1:00", "2:00",
                                Instructor("STAFF", "STAFF"))
                )

                val conflicts = checkInstructors(classes, emptyList())
                conflicts.isEmpty().shouldBeTrue()

            }
        }

        "rooms" should {
            "not have conflicts if no overlaps" {
                val classes = listOf(
                        createDummyClass("cs", "121", "1", room = "a"),
                        createDummyClass("cs", "121", "1", room = "b"),
                        createDummyClass("cs", "121", "1", room = "c")
                )

                checkRooms(classes, emptyList()).isEmpty().shouldBeTrue()
            }

            "not have conflicts if ignored" {
                val classes = listOf(
                        createDummyClass("cs", "121", "1", room = "a"),
                        createDummyClass("cs", "121", "1", room = "a")
                )

                val constraints = listOf(
                        createConstraint(1, ConstraintPriority.IGNORE, "cs121")
                )

                checkRooms(classes, constraints).isEmpty().shouldBeTrue()
            }

            "have conflicts if overlaps" {
                val classes = listOf(
                        createDummyClass("cs", "121", "1", "1:00", "2:00", "a"),
                        createDummyClass("cs", "121", "2", "1:30", "2:30", "a"),
                        createDummyClass("cs", "121", "3", "4:00", "5:00", "b"),
                        createDummyClass("cs", "121", "4", "4:20", "5:00", "b"),
                        createDummyClass("cs", "121", "5", "3:20", "3:30", "c")
                )

                val expectedA = setOf(
                        listOf(classes[0], classes[1])
                )

                val expectedB = setOf(
                        listOf(classes[2], classes[3])
                )

                val conflicts = checkRooms(classes, emptyList())

                conflicts.size.shouldBe(2)
                checkOverlapsAreEqual(conflicts.getValue("a"), expectedA).shouldBeTrue()
                checkOverlapsAreEqual(conflicts.getValue("b"), expectedB).shouldBeTrue()
            }

            "ignore blank room names" {
                val classes = listOf(
                        createDummyClass("cs", "121", "1", "1:00", "2:00", ""),
                        createDummyClass("cs", "121", "2", "1:30", "2:30", "")
                )
                val conflicts = checkRooms(classes, emptyList())
                conflicts.isEmpty().shouldBeTrue()
            }
        }

        "class date ranges" should {
            "not cause conflicts if no overlap" {
                val classes = listOf(
                        createDummyClass(1, 2),
                        createDummyClass(3, 4),
                        createDummyClass(5, 6)
                )

                findDateConflicts(classes).isEmpty().shouldBeTrue()
            }

            "cause conflicts if there is overlap" {
                val classes = listOf(
                        createDummyClass(1, 2),
                        createDummyClass(2, 3)
                )

                checkOverlapsAreEqual(findDateConflicts(classes), setOf(classes)).shouldBeTrue()
            }

            "find multiple conflicts with multiple date ranges: {" {
                val classes = listOf(
                        createDummyClass(1, 2, "1"),
                        createDummyClass(1, 2, "2"),
                        createDummyClass(3, 4, "1"),
                        createDummyClass(3, 4, "2")
                )

                val expected = setOf(
                        listOf(
                                classes[0],
                                classes[1]),
                        listOf(
                                classes[2],
                                classes[3])
                )

                checkOverlapsAreEqual(findDateConflicts(classes), expected).shouldBeTrue()

            }
        }
    }
}

