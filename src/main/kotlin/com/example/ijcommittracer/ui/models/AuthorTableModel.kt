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
        CommitTracerBundle.message("dialog.column.author"), // Author first
        CommitTracerBundle.message("dialog.column.author.commits"), // Commits second
        "W Tests", 
        "% W Tests",
        "Name",
        "Team",
        "Title",
        CommitTracerBundle.message("dialog.column.author.tickets"),
        "Blockers",
        "Regressions",
        CommitTracerBundle.message("dialog.column.author.first"),
        CommitTracerBundle.message("dialog.column.author.last"),
        CommitTracerBundle.message("dialog.column.author.days"),
        CommitTracerBundle.message("dialog.column.author.avg")
    )
    
    // Use Locale.US for consistent date formatting
    private val dateFormat = SimpleDateFormat("dd/MM/yy", Locale.US)
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
            1, 2, 7, 8, 9, 12 -> Integer::class.java  // Commits, W Tests, Tickets, Blockers, Regressions, Active Days
            3, 13 -> Double::class.java  // % W Tests and Commits/Day
            else -> String::class.java
        }
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val author = authors[rowIndex]
        return when (columnIndex) {
            0 -> author.author
            1 -> author.commitCount
            2 -> author.testTouchedCount
            3 -> {
                val testPercentage = author.getTestCoveragePercentage()
                commitsDayFormat.format(testPercentage).toDouble()
            }
            4 -> author.displayName.ifBlank { "Unknown" }
            5 -> author.teamName.ifBlank { "Unknown" }
            6 -> author.title.ifBlank { "Unknown" }
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