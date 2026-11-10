package Com.ak.testseries.exam

/**
 * Builds a deterministic subject/section allocation from the registered exam pattern.
 * It never invents subject weights or silently routes "All Subjects" to the first subject.
 */
object UniversalQuestionGenerationPlanner {

    data class SectionAllocation(
        val subjectId: String,
        val subjectName: String,
        val sectionId: String,
        val sectionName: String,
        val questionType: QuestionType,
        val optionCount: Int,
        val positiveMarks: Double,
        val negativeMarks: Double,
        val questionCount: Int
    )

    data class Plan(
        val examId: String,
        val stageId: String,
        val paperId: String,
        val totalQuestions: Int,
        val allocations: List<SectionAllocation>
    ) {
        val subjectCounts: Map<String, Int>
            get() = allocations.groupBy { it.subjectId }.mapValues { (_, rows) -> rows.sumOf { it.questionCount } }

        fun batch(offset: Int, count: Int): List<SectionAllocation> {
            require(offset >= 0 && count > 0 && offset + count <= totalQuestions) {
                "Question batch range is outside the generated test plan."
            }
            val batchStart = offset
            val batchEnd = offset + count
            var cursor = 0
            return allocations.mapNotNull { allocation ->
                val allocationStart = cursor
                val allocationEnd = cursor + allocation.questionCount
                cursor = allocationEnd
                val overlap = (minOf(batchEnd, allocationEnd) - maxOf(batchStart, allocationStart)).coerceAtLeast(0)
                allocation.takeIf { overlap > 0 }?.copy(questionCount = overlap)
            }
        }

        fun batchInstruction(offset: Int, count: Int): String {
            val rows = batch(offset, count)
            val details = rows.joinToString("\n") {
                "- subjectId=" + it.subjectId + "; subject=" + it.subjectName +
                    "; sectionId=" + it.sectionId + "; section=" + it.sectionName +
                    "; type=" + it.questionType.name + "; options=" + it.optionCount +
                    "; positiveMarks=" + it.positiveMarks + "; negativeMarks=" + it.negativeMarks +
                    "; exactCount=" + it.questionCount
            }
            return """
                AUTHORITATIVE SUBJECT/SECTION DISTRIBUTION FOR THIS BATCH:
                $details
                Generate exactly $count questions and obey every exactCount above.
                Every question MUST include subjectId, subject, sectionId, sectionName, questionType,
                positiveMarks and negativeMarks exactly as listed. Do not use any other subject or section.
            """.trimIndent()
        }

        fun matches(actual: List<Pair<String, String>>): Boolean {
            if (actual.size != totalQuestions) return false
            val expected = allocations.associate { (it.subjectId to it.sectionId) to it.questionCount }
            val observed = actual.groupingBy { it.first to it.second }.eachCount()
            return expected.filterValues { it > 0 } == observed
        }
    }

    fun create(
        exam: ExamConfig,
        stage: StageConfig,
        paper: PaperConfig,
        selectedSubject: String,
        requestedQuestions: Int
    ): Plan {
        require(requestedQuestions > 0) { "Question count must be greater than zero." }
        require(exam.stages.any { it.stageId == stage.stageId }) { "Stage does not belong to the selected exam." }
        require(stage.papers.any { it.paperId == paper.paperId }) { "Paper does not belong to the selected stage." }

        val subjects = if (selectedSubject.equals("All Subjects", ignoreCase = true)) {
            paper.subjects
        } else {
            listOfNotNull(
                paper.subjects.singleOrNull {
                    it.subjectId.equals(selectedSubject, true) || it.subjectName.equals(selectedSubject, true)
                }
            )
        }
        require(subjects.isNotEmpty()) { "Selected subject is not configured in this paper." }
        require(subjects.all { it.sections.isNotEmpty() }) { "Every selected subject must have configured sections." }

        val totalCapacity = subjects.sumOf { subject -> subject.sections.sumOf { it.totalQuestions.coerceAtLeast(0) } }
        require(requestedQuestions <= totalCapacity) {
            "Requested $requestedQuestions questions exceed the configured capacity of $totalCapacity."
        }

        val compulsoryCapacity = subjects.sumOf { subject ->
            subject.sections.sumOf { it.compulsoryQuestions.coerceIn(0, it.totalQuestions.coerceAtLeast(0)) }
        }
        val subjectCaps = subjects.map { subject ->
            subject.sections.sumOf { it.totalQuestions.coerceAtLeast(0) }
        }
        val subjectCompulsory = subjects.map { subject ->
            subject.sections.sumOf { it.compulsoryQuestions.coerceIn(0, it.totalQuestions.coerceAtLeast(0)) }
        }

        val subjectCounts = if (requestedQuestions <= compulsoryCapacity) {
            allocateWeighted(requestedQuestions, subjectCompulsory, subjectCompulsory)
        } else {
            val extras = allocateWeighted(
                requestedQuestions - compulsoryCapacity,
                subjectCaps.indices.map { subjectCaps[it] - subjectCompulsory[it] },
                subjectCaps.indices.map { subjectCaps[it] - subjectCompulsory[it] }
            )
            subjectCompulsory.indices.map { subjectCompulsory[it] + extras[it] }
        }

        val result = mutableListOf<SectionAllocation>()
        subjects.forEachIndexed { subjectIndex, subject ->
            val sections = subject.sections
            val caps = sections.map { it.totalQuestions.coerceAtLeast(0) }
            val compulsory = sections.map { it.compulsoryQuestions.coerceIn(0, it.totalQuestions.coerceAtLeast(0)) }
            val subjectCount = subjectCounts[subjectIndex]
            val sectionCounts = if (subjectCount <= compulsory.sum()) {
                allocateWeighted(subjectCount, compulsory, compulsory)
            } else {
                val extras = allocateWeighted(
                    subjectCount - compulsory.sum(),
                    caps.indices.map { caps[it] - compulsory[it] },
                    caps.indices.map { caps[it] - compulsory[it] }
                )
                compulsory.indices.map { compulsory[it] + extras[it] }
            }
            sections.forEachIndexed { sectionIndex, section ->
                val count = sectionCounts[sectionIndex]
                if (count > 0) {
                    require(section.optionsCount == 0 || section.optionsCount in 4..5) {
                        "Section " + section.sectionId + " has an unsupported option count."
                    }
                    result.add(
                        SectionAllocation(
                            subjectId = subject.subjectId,
                            subjectName = subject.subjectName,
                            sectionId = section.sectionId,
                            sectionName = section.sectionName,
                            questionType = section.questionType,
                            optionCount = section.optionsCount,
                            positiveMarks = section.positiveMarks,
                            negativeMarks = section.negativeMarks,
                            questionCount = count
                        )
                    )
                }
            }
        }

        require(result.sumOf { it.questionCount } == requestedQuestions) {
            "Could not allocate the exact requested question count from the exam pattern."
        }
        return Plan(exam.examId, stage.stageId, paper.paperId, requestedQuestions, result)
    }

    private fun allocateWeighted(total: Int, weights: List<Int>, capacities: List<Int>): List<Int> {
        require(weights.size == capacities.size)
        if (total == 0) return List(weights.size) { 0 }
        val allocation = MutableList(weights.size) { 0 }
        var remaining = total

        while (remaining > 0) {
            val active = capacities.indices.filter { allocation[it] < capacities[it] }
            require(active.isNotEmpty()) { "Question allocation exceeds configured section capacity." }
            val effectiveWeights = active.map { index -> weights[index].coerceAtLeast(0) }
            val weightSum = effectiveWeights.sum().takeIf { it > 0 }
                ?: active.sumOf { capacities[it] - allocation[it] }
            val actualWeights = if (effectiveWeights.sum() > 0) effectiveWeights
                else active.map { capacities[it] - allocation[it] }

            val quotas = active.mapIndexed { position, index ->
                val quota = remaining.toDouble() * actualWeights[position] / weightSum
                index to quota
            }
            var assignedThisRound = 0
            quotas.forEach { (index, quota) ->
                val floor = kotlin.math.floor(quota).toInt()
                val room = capacities[index] - allocation[index]
                val add = minOf(floor, room, remaining - assignedThisRound)
                if (add > 0) {
                    allocation[index] += add
                    assignedThisRound += add
                }
            }
            remaining -= assignedThisRound

            if (remaining > 0) {
                val remainderOrder = quotas.sortedByDescending { it.second - kotlin.math.floor(it.second) }
                var progressed = false
                for ((index, _) in remainderOrder) {
                    if (remaining == 0) break
                    if (allocation[index] < capacities[index]) {
                        allocation[index] += 1
                        remaining -= 1
                        progressed = true
                    }
                }
                require(progressed || assignedThisRound > 0) {
                    "Question allocation could not satisfy the configured capacities."
                }
            }
        }
        return allocation
    }
}
