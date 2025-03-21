package com.example.ijcommittracer.ui.models

import com.example.ijcommittracer.CommitTracerBundle
import com.example.ijcommittracer.actions.ListCommitsAction.AuthorStats
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.table.AbstractTableModel

/**
 * Table model for displaying author statistics.
 */
class AuthorTableModel(private var authors: List<AuthorStats>) : AbstractTableModel() {
    private val columns = arrayOf(
        CommitTracerBundle.message("dialog.column.author"),
        "W Tests",  // Moved from pos 8 and renamed from "Commits with Tests"
        "% W Tests", // Moved from pos 9 and renamed from "Test %"
        "Name",
        "Team",
        "Title",
        CommitTracerBundle.message("dialog.column.author.commits"),
        CommitTracerBundle.message("dialog.column.author.tickets"),
        "Blockers",
        "Regressions",
        CommitTracerBundle.message("dialog.column.author.first"),
        CommitTracerBundle.message("dialog.column.author.last"),
        CommitTracerBundle.message("dialog.column.author.days"),
        CommitTracerBundle.message("dialog.column.author.avg")
    )
    
    // Use Locale.US for consistent date formatting
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val commitsDayFormat = DecimalFormat("0.00", DecimalFormatSymbols(Locale.US))
    
    fun updateData(newAuthors: List<AuthorStats>) {
        authors = newAuthors
        fireTableDataChanged()
    }

    override fun getRowCount(): Int = authors.size

    override fun getColumnCount(): Int = columns.size

    override fun getColumnName(column: Int): String = columns[column]
    
    override fun getColumnClass(columnIndex: Int): Class<*> {
        return when (columnIndex) {
            1, 6, 7, 8, 9, 12 -> Integer::class.java  // W Tests, Commits, Tickets, Blockers, Regressions, Active Days
            2, 13 -> Double::class.java  // % W Tests and Commits/Day
            else -> String::class.java
        }
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val author = authors[rowIndex]
        return when (columnIndex) {
            0 -> author.author
            1 -> author.testTouchedCount
            2 -> {
                val testPercentage = author.getTestCoveragePercentage()
                commitsDayFormat.format(testPercentage).toDouble()
            }
            3 -> author.displayName.ifBlank { "Unknown" }
            4 -> author.teamName.ifBlank { "Unknown" }
            5 -> author.title.ifBlank { "Unknown" }
            6 -> author.commitCount
            7 -> author.youTrackTickets.size
            8 -> author.getBlockerCount()
            9 -> author.getRegressionCount()
            10 -> dateFormat.format(author.firstCommitDate)
            11 -> dateFormat.format(author.lastCommitDate)
            12 -> author.getActiveDays()
            13 -> {
                val commitsPerDay = author.getCommitsPerDay()
                // Format for display while maintaining the Double type for sorting
                commitsDayFormat.format(commitsPerDay).toDouble()
            }
            else -> ""
        }
    }
}