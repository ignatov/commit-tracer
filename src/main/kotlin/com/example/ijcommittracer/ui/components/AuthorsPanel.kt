package com.example.ijcommittracer.ui.components

import com.example.ijcommittracer.CommitTracerBundle
import com.example.ijcommittracer.actions.ListCommitsAction.AuthorStats
import com.example.ijcommittracer.actions.ListCommitsAction.ChangedFileInfo
import com.example.ijcommittracer.actions.ListCommitsAction.ChangeType
import com.example.ijcommittracer.actions.ListCommitsAction.CommitInfo
import com.example.ijcommittracer.services.NotificationService
import com.example.ijcommittracer.ui.models.AuthorTableModel
import com.example.ijcommittracer.ui.models.CommitTableModel
import com.example.ijcommittracer.ui.models.TicketsTableModel
import com.example.ijcommittracer.ui.util.TicketInfo
import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*
import javax.swing.*
import javax.swing.event.ChangeEvent
import javax.swing.event.DocumentListener
import javax.swing.event.ListSelectionListener
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableModel
import javax.swing.table.TableRowSorter

/**
 * Panel for displaying author statistics.
 */
class AuthorsPanel(
    private val authorStats: Map<String, AuthorStats>,
    private val commits: List<CommitInfo>,
    // Lambda to update statistics in parent dialog
    private val updateStatsCallback: ((totalAuthors: Int, totalCommits: Int, totalTickets: Int, 
                                      avgCommits: String, medianCommits: String) -> Unit)? = null
) : JPanel(BorderLayout()) {
    
    private lateinit var authorsTable: JBTable
    private lateinit var authorCommitsTable: JBTable
    private lateinit var ticketsTable: JBTable
    private var authorCommitsSelectionListener: ListSelectionListener? = null
    private var ticketCommitsSelectionListener: ListSelectionListener? = null
    private val filteredCommits: List<CommitInfo> = commits
    
    init {
        initialize()
    }
    
    /**
     * Adds a context menu to a table with copy functionality
     */
    private fun addTableContextMenu(table: JTable) {
        val popupMenu = JPopupMenu()
        val copyMenuItem = JMenuItem(CommitTracerBundle.message("dialog.copy.to.clipboard.csv"))
        copyMenuItem.addActionListener { copyTableToClipboard(table) }
        popupMenu.add(copyMenuItem)
        
        // Show the menu on right-click
        table.componentPopupMenu = popupMenu
    }
    
    /**
     * Adds a context menu to a list with copy functionality
     */
    private fun addListContextMenu(list: JList<*>) {
        val popupMenu = JPopupMenu()
        val copyMenuItem = JMenuItem(CommitTracerBundle.message("dialog.copy.to.clipboard"))
        copyMenuItem.addActionListener { copyListToClipboard(list) }
        popupMenu.add(copyMenuItem)
        
        // Show the menu on right-click
        list.componentPopupMenu = popupMenu
    }
    
    /**
     * Utility function to copy list data as CSV to clipboard
     */
    private fun copyListToClipboard(list: JList<*>) {
        val selectedIndices = list.selectedIndices
        
        // Check if the list is empty
        if (list.model.size == 0) {
            NotificationService.showInfo(null, CommitTracerBundle.message("dialog.notification.no.data"), "Commit Tracer")
            return
        }
        
        // If nothing is selected, select all
        val indices = if (selectedIndices.isEmpty()) (0 until list.model.size).toList() else selectedIndices.toList()
        
        val sb = StringBuilder()
        
        // Add each selected item to the CSV
        for (i in indices.indices) {
            val item = list.model.getElementAt(indices[i])
            
            // Handle ChangedFileInfo specially
            val text = when (item) {
                is ChangedFileInfo -> {
                    val prefix = when(item.changeType) {
                        ChangeType.ADDED -> "[+]"
                        ChangeType.DELETED -> "[-]"
                        ChangeType.MODIFIED -> "[M]"
                    }
                    val testMarker = if (item.isTestFile) "[Test]" else ""
                    "$prefix $testMarker ${item.path}"
                }
                else -> item.toString()
            }
            
            // Properly escape and quote each item
            sb.append("\"${escapeCSV(text)}\"")
            
            if (i < indices.size - 1) sb.append("\n")
        }
        
        // Copy to clipboard
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val selection = StringSelection(sb.toString())
        clipboard.setContents(selection, null)
        
        // Show notification
        NotificationService.showInfo(null, CommitTracerBundle.message("dialog.notification.copied"), "Commit Tracer")
    }
    
    /**
     * Utility function to copy table data as CSV to clipboard
     */
    private fun copyTableToClipboard(table: JTable, includeHeaders: Boolean = true) {
        val selectedRows = table.selectedRows
        val selectedColumns = table.selectedColumns
        
        // Check if the table is empty
        if (table.rowCount == 0) {
            NotificationService.showInfo(null, CommitTracerBundle.message("dialog.notification.no.data"), "Commit Tracer")
            return
        }
        
        // If nothing is selected, select all
        val rows = if (selectedRows.isEmpty()) (0 until table.rowCount).toList() else selectedRows.toList()
        val columns = if (selectedColumns.isEmpty()) (0 until table.columnCount).toList() else selectedColumns.toList()
        
        val model = table.model
        val sb = StringBuilder()
        
        // Add headers if requested
        if (includeHeaders) {
            for (colIndex in columns.indices) {
                if (colIndex > 0) sb.append(",")
                // Properly escape and quote column headers
                sb.append("\"${escapeCSV(model.getColumnName(columns[colIndex]))}\"")
            }
            sb.append("\n")
        }
        
        // Add selected data
        for (rowIndex in rows.indices) {
            val modelRow = if (table.rowSorter != null) table.convertRowIndexToModel(rows[rowIndex]) else rows[rowIndex]
            
            for (colIndex in columns.indices) {
                if (colIndex > 0) sb.append(",")
                
                // Get cell value and convert to string
                val value = model.getValueAt(modelRow, columns[colIndex])
                val cellText = when (value) {
                    is Boolean -> if (value) "Yes" else "No"
                    else -> value.toString()
                }
                
                // Properly escape and quote each cell
                sb.append("\"${escapeCSV(cellText)}\"")
            }
            if (rowIndex < rows.size - 1) sb.append("\n")
        }
        
        // Copy to clipboard
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val selection = StringSelection(sb.toString())
        clipboard.setContents(selection, null)
        
        // Show notification
        NotificationService.showInfo(null, CommitTracerBundle.message("dialog.notification.copied"), "Commit Tracer")
    }
    
    /**
     * Escapes double quotes in CSV cells by doubling them
     */
    private fun escapeCSV(text: String): String {
        return text.replace("\"", "\"\"")
    }
    
    private fun initialize() {
        // Extract HiBob info presence for UI customization
        val hasHiBobInfo = authorStats.values.any { 
            it.teamName.isNotBlank() || it.displayName.isNotBlank() || it.title.isNotBlank() 
        }
        
        // Create table with author statistics
        val authorList = authorStats.values.toList()
        val tableModel = AuthorTableModel(authorList)
        
        authorsTable = JBTable(tableModel).apply {
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            
            // Add Ctrl+C copy support
            registerKeyboardAction(
                { copyTableToClipboard(this) },
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx),
                JComponent.WHEN_FOCUSED
            )
            
            // Add context menu for right-click copy
            addTableContextMenu(this)
            
            // Set column widths
            columnModel.getColumn(0).preferredWidth = 200 // Author (email)
            columnModel.getColumn(1).preferredWidth = 80  // Commits count
            
            // Set preferred width for W Tests and % W Tests columns
            columnModel.getColumn(2).preferredWidth = 80  // W Tests
            columnModel.getColumn(3).preferredWidth = 80  // % W Tests
            
            // Only show HiBob columns if we have the information
            if (hasHiBobInfo) {
                columnModel.getColumn(4).preferredWidth = 150 // Name
                columnModel.getColumn(5).preferredWidth = 120 // Team
                columnModel.getColumn(6).preferredWidth = 150 // Title
            } else {
                // Hide name, team, title columns if no HiBob info
                columnModel.removeColumn(columnModel.getColumn(6)) // Remove Title
                columnModel.removeColumn(columnModel.getColumn(5)) // Remove Team
                columnModel.removeColumn(columnModel.getColumn(4)) // Remove Name
            }
            
            // Set column widths - need to adjust indices if HiBob columns are hidden
            val authorCol = 0
            val commitCountCol = 1
            val wTestsCol = 2
            val wTestsPercentCol = 3
            val ticketsCountCol = if (hasHiBobInfo) 7 else 4
            val blockersCountCol = if (hasHiBobInfo) 8 else 5
            val regressionsCountCol = if (hasHiBobInfo) 9 else 6
            val firstCommitCol = if (hasHiBobInfo) 10 else 7
            val lastCommitCol = if (hasHiBobInfo) 11 else 8
            val activeDaysCol = if (hasHiBobInfo) 12 else 9
            val commitsPerDayCol = if (hasHiBobInfo) 13 else 10
            
            columnModel.getColumn(commitCountCol).preferredWidth = 80  // Commit Count
            columnModel.getColumn(ticketsCountCol).preferredWidth = 80  // Tickets Count
            columnModel.getColumn(blockersCountCol).preferredWidth = 80  // Blockers Count
            columnModel.getColumn(regressionsCountCol).preferredWidth = 80  // Regressions Count
            columnModel.getColumn(firstCommitCol).preferredWidth = 150 // First Commit
            columnModel.getColumn(lastCommitCol).preferredWidth = 150 // Last Commit
            columnModel.getColumn(activeDaysCol).preferredWidth = 80  // Active Days
            columnModel.getColumn(commitsPerDayCol).preferredWidth = 120 // Commits/Day
            
            // Center-align numeric columns
            val centerRenderer = DefaultTableCellRenderer()
            centerRenderer.horizontalAlignment = SwingConstants.CENTER
            columnModel.getColumn(commitCountCol).cellRenderer = centerRenderer // Commit Count
            columnModel.getColumn(wTestsCol).cellRenderer = centerRenderer // W Tests
            columnModel.getColumn(ticketsCountCol).cellRenderer = centerRenderer // Tickets Count
            columnModel.getColumn(blockersCountCol).cellRenderer = centerRenderer // Blockers Count
            columnModel.getColumn(regressionsCountCol).cellRenderer = centerRenderer // Regressions Count
            
            // Create a custom renderer for the W Tests % column with color-coding
            val testCoverageRenderer = object : DefaultTableCellRenderer() {
                // Use a local formatter for percentage values
                private val percentageFormat = DecimalFormat("0.00", DecimalFormatSymbols(Locale.US))
                
                override fun getTableCellRendererComponent(
                    table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
                ): Component {
                    val label = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) as JLabel
                    label.horizontalAlignment = SwingConstants.CENTER
                    
                    if (value is Number) {
                        val percentage = value.toDouble()
                        label.text = percentageFormat.format(percentage) + "%"
                        
                        // Color code based on test coverage percentage
                        when {
                            percentage >= 75.0 -> label.foreground = JBColor.GREEN.darker()
                            percentage >= 50.0 -> label.foreground = JBColor.ORANGE
                            percentage >= 25.0 -> label.foreground = JBColor.YELLOW.darker()
                            else -> label.foreground = JBColor.RED
                        }
                    }
                    
                    return label
                }
            }
            columnModel.getColumn(wTestsPercentCol).cellRenderer = testCoverageRenderer // % W Tests
            
            columnModel.getColumn(activeDaysCol).cellRenderer = centerRenderer // Active Days
            
            // Create special renderer for commits/day with 2 decimal places
            val commitsPerDayRenderer = DefaultTableCellRenderer().apply {
                horizontalAlignment = SwingConstants.CENTER
            }
            columnModel.getColumn(commitsPerDayCol).cellRenderer = commitsPerDayRenderer // Commits/Day
            
            // Add date renderer for date columns to ensure consistent display
            val dateRenderer = DefaultTableCellRenderer()
            dateRenderer.horizontalAlignment = SwingConstants.CENTER
            columnModel.getColumn(firstCommitCol).cellRenderer = dateRenderer // First Commit
            columnModel.getColumn(lastCommitCol).cellRenderer = dateRenderer // Last Commit
            
            // Add team renderer with distinctive background, but only if HiBob info is available
            if (hasHiBobInfo) {
                val teamRenderer = DefaultTableCellRenderer().apply {
                    horizontalAlignment = SwingConstants.LEFT
                    background = JBColor(0xE8F4FE, 0x2D3A41)  // Light blue in light theme, dark blue in dark theme
                }
                columnModel.getColumn(2).cellRenderer = teamRenderer // Team
            }
            
            // Add row sorter for sorting with appropriate comparators
            val sorter = TableRowSorter(tableModel)
            
            // Make sure numeric columns are sorted as numbers - handle various data types safely
            sorter.setComparator(commitCountCol, Comparator.comparingInt<Any> { 
                when (it) {
                    is Number -> it.toInt()
                    is String -> it.toString().toIntOrNull() ?: 0
                    else -> 0
                }
            }) // Commits
            
            sorter.setComparator(ticketsCountCol, Comparator.comparingInt<Any> { 
                when (it) {
                    is Number -> it.toInt()
                    is String -> it.toString().toIntOrNull() ?: 0
                    else -> 0
                }
            }) // Tickets Count
            
            sorter.setComparator(blockersCountCol, Comparator.comparingInt<Any> { 
                when (it) {
                    is Number -> it.toInt()
                    is String -> it.toString().toIntOrNull() ?: 0
                    else -> 0
                }
            }) // Blockers Count
            
            sorter.setComparator(regressionsCountCol, Comparator.comparingInt<Any> { 
                when (it) {
                    is Number -> it.toInt()
                    is String -> it.toString().toIntOrNull() ?: 0
                    else -> 0
                }
            }) // Regressions Count
            
            sorter.setComparator(wTestsCol, Comparator.comparingInt<Any> { 
                when (it) {
                    is Number -> it.toInt()
                    is String -> it.toString().toIntOrNull() ?: 0
                    else -> 0
                }
            }) // W Tests column
            
            // W Tests % - use numeric value for sorting
            sorter.setComparator(wTestsPercentCol, Comparator.comparingDouble<Any> {
                when (it) {
                    is Number -> it.toDouble()
                    is String -> it.toDoubleOrNull() ?: 0.0
                    else -> 0.0
                }
            })
            
            sorter.setComparator(activeDaysCol, Comparator.comparingInt<Any> { 
                when (it) {
                    is Number -> it.toInt()
                    is String -> it.toString().toIntOrNull() ?: 0
                    else -> 0
                }
            }) // Active Days
            
            // Commits/Day - still use the numeric value for sorting (the formatted value is still a Double)
            sorter.setComparator(commitsPerDayCol, Comparator.comparingDouble<Any> {
                when (it) {
                    is Number -> it.toDouble()
                    is String -> it.toDoubleOrNull() ?: 0.0
                    else -> 0.0
                }
            })
            
            // Team comparator - only if HiBob info is available
            if (hasHiBobInfo) {
                sorter.setComparator(2, Comparator.comparing(String::toString, String.CASE_INSENSITIVE_ORDER))
            }
            
            // Sort by commit count (descending) by default
            sorter.toggleSortOrder(commitCountCol)
            sorter.toggleSortOrder(commitCountCol) // Toggle twice to get descending order
            
            rowSorter = sorter
        }
        
        // Create tabbed panel for details
        val authorDetailsTabbedPane = JBTabbedPane()
        
        // Create author details panel
        val authorCommitsPanel = JPanel(BorderLayout())
        authorCommitsPanel.border = JBUI.Borders.empty(5)
        
        // Panel for label and search field
        val authorCommitsHeaderPanel = JPanel(BorderLayout())
        val authorCommitsLabel = JBLabel(CommitTracerBundle.message("dialog.author.commits", "", "0"))
        authorCommitsHeaderPanel.add(authorCommitsLabel, BorderLayout.WEST)
        
        // Add search field for filtering author's commits
        val authorCommitsSearchPanel = JPanel(BorderLayout())
        val authorCommitsSearchLabel = JBLabel(CommitTracerBundle.message("dialog.filter.search"))
        authorCommitsSearchLabel.border = JBUI.Borders.empty(0, 5)
        authorCommitsSearchPanel.add(authorCommitsSearchLabel, BorderLayout.WEST)
        
        val authorCommitsSearchField = JTextField().apply {
            preferredSize = Dimension(150, preferredSize.height)
            addKeyListener(object : java.awt.event.KeyAdapter() {
                override fun keyPressed(e: java.awt.event.KeyEvent) {
                    if (e.keyCode == java.awt.event.KeyEvent.VK_ESCAPE) {
                        text = ""
                    }
                }
            })
        }
        authorCommitsSearchPanel.add(authorCommitsSearchField, BorderLayout.CENTER)
        authorCommitsHeaderPanel.add(authorCommitsSearchPanel, BorderLayout.EAST)
        
        authorCommitsPanel.add(authorCommitsHeaderPanel, BorderLayout.NORTH)
        
        // Create author commits table
        authorCommitsTable = JBTable().apply {
            // Add Ctrl+C copy support
            registerKeyboardAction(
                { copyTableToClipboard(this) },
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx),
                JComponent.WHEN_FOCUSED
            )
            
            // Add context menu for right-click copy
            addTableContextMenu(this)
        }
        val authorCommitsScrollPane = JBScrollPane(authorCommitsTable)
        
        // Create changed files panel
        val changedFilesPanel = JPanel(BorderLayout())
        changedFilesPanel.border = JBUI.Borders.empty(5, 0, 0, 0)
        
        val changedFilesLabel = JBLabel(CommitTracerBundle.message("dialog.changed.files"))
        changedFilesLabel.border = JBUI.Borders.empty(0, 0, 5, 0)
        changedFilesPanel.add(changedFilesLabel, BorderLayout.NORTH)
        
        // Create a list model and JList for the changed files
        val changedFilesListModel = DefaultListModel<ChangedFileInfo>()
        val changedFilesList = JBList<ChangedFileInfo>(changedFilesListModel).apply {
            // Add Ctrl+C copy support
            registerKeyboardAction(
                { copyListToClipboard(this) },
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx),
                JComponent.WHEN_FOCUSED
            )
            
            // Add context menu for right-click copy
            addListContextMenu(this)
            
            setCellRenderer(object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>,
                    value: Any?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean
                ): Component {
                    val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                    if (value is ChangedFileInfo && component is JLabel) {
                        // Add appropriate icons and formatting based on change type
                        when (value.changeType) {
                            ChangeType.ADDED -> {
                                component.text = "[+] ${value.path}"
                                component.icon = AllIcons.Actions.AddFile
                            }
                            ChangeType.DELETED -> {
                                component.text = "[-] ${value.path}"
                                component.icon = AllIcons.Actions.DeleteTag
                            }
                            ChangeType.MODIFIED -> {
                                component.text = "[M] ${value.path}"
                                component.icon = AllIcons.Actions.Edit
                            }
                        }
                        
                        // Set text color to green for test files
                        if (value.isTestFile) {
                            component.foreground = JBColor.GREEN.darker()
                            // Override the change type icon with the test icon for test files
                            component.icon = AllIcons.Nodes.JunitTestMark
                        }
                    }
                    return component
                }
            })
        }
        
        changedFilesPanel.add(JBScrollPane(changedFilesList), BorderLayout.CENTER)
        
        // Split the author commits panel vertically
        val splitPane = OnePixelSplitter(true, 0.6f)
        splitPane.firstComponent = authorCommitsScrollPane
        splitPane.secondComponent = changedFilesPanel
        authorCommitsPanel.add(splitPane, BorderLayout.CENTER)
        
        // Create YouTrack tickets panel
        val ticketsPanel = JPanel(BorderLayout())
        ticketsPanel.border = JBUI.Borders.empty(5)
        
        // Panel for label and search field
        val ticketsHeaderPanel = JPanel(BorderLayout())
        val ticketsLabel = JBLabel(CommitTracerBundle.message("dialog.youtrack.tickets", "0"))
        ticketsHeaderPanel.add(ticketsLabel, BorderLayout.WEST)
        
        // Add search field for filtering tickets
        val ticketsSearchPanel = JPanel(BorderLayout())
        val ticketsSearchLabel = JBLabel(CommitTracerBundle.message("dialog.filter.search"))
        ticketsSearchLabel.border = JBUI.Borders.empty(0, 5)
        ticketsSearchPanel.add(ticketsSearchLabel, BorderLayout.WEST)
        
        val ticketsSearchField = JTextField().apply {
            preferredSize = Dimension(150, preferredSize.height)
            addKeyListener(object : java.awt.event.KeyAdapter() {
                override fun keyPressed(e: java.awt.event.KeyEvent) {
                    if (e.keyCode == java.awt.event.KeyEvent.VK_ESCAPE) {
                        text = ""
                    }
                }
            })
        }
        ticketsSearchPanel.add(ticketsSearchField, BorderLayout.CENTER)
        ticketsHeaderPanel.add(ticketsSearchPanel, BorderLayout.EAST)
        
        ticketsPanel.add(ticketsHeaderPanel, BorderLayout.NORTH)
        
        ticketsTable = JBTable().apply {
            // Add Ctrl+C copy support
            registerKeyboardAction(
                { copyTableToClipboard(this) },
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx),
                JComponent.WHEN_FOCUSED
            )
            
            // Add context menu for right-click copy
            addTableContextMenu(this)
        }
        val ticketsScrollPane = JBScrollPane(ticketsTable)
        ticketsPanel.add(ticketsScrollPane, BorderLayout.CENTER)
        
        // Add tabs to the tabbed pane
        authorDetailsTabbedPane.addTab(CommitTracerBundle.message("dialog.tab.all.commits"), authorCommitsPanel)
        authorDetailsTabbedPane.addTab("YouTrack Tickets", ticketsPanel)
        
        // Add a change listener to clear the changed files panel when switching tabs
        authorDetailsTabbedPane.addChangeListener { _: ChangeEvent -> 
            // Clear the changed files panel when changing tabs
            changedFilesListModel.clear()
            changedFilesLabel.text = CommitTracerBundle.message("dialog.changed.files")
        }
        
        // Add selection listener to show author details
        authorsTable.selectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val selectedRow = authorsTable.selectedRow
                if (selectedRow >= 0) {
                    val modelRow = authorsTable.convertRowIndexToModel(selectedRow)
                    val author = authorStats.values.toList()[modelRow]
                    
                    // Filter commits for this author
                    val authorCommits = filteredCommits.filter { it.author == author.author }
                    
                    // Update author commits table
                    val authorCommitsModel = CommitTableModel(authorCommits)
                    authorCommitsTable.model = authorCommitsModel
                    
                    // Clear the changed files panel when author selection changes
                    changedFilesListModel.clear()
                    changedFilesLabel.text = CommitTracerBundle.message("dialog.changed.files")
                    
                    // Select the first commit by default if available
                    if (authorCommits.isNotEmpty()) {
                        SwingUtilities.invokeLater {
                            if (authorCommitsTable.rowCount > 0) {
                                try {
                                    authorCommitsTable.setRowSelectionInterval(0, 0)
                                    authorCommitsTable.scrollRectToVisible(authorCommitsTable.getCellRect(0, 0, true))
                                    
                                    // Manually update the changed files list for the first commit
                                    val selectedCommit = authorCommits[0]
                                    changedFilesListModel.clear()
                                    if (selectedCommit.changedFiles.isNotEmpty()) {
                                        // Sort files by path, with test files first
                                        val sortedFiles = selectedCommit.changedFiles.sortedWith(
                                            compareByDescending<ChangedFileInfo> { it.isTestFile }
                                            .thenBy { it.path.lowercase() }
                                        )
                                        sortedFiles.forEach { changedFilesListModel.addElement(it) }
                                        changedFilesLabel.text = "${CommitTracerBundle.message("dialog.changed.files")} (${selectedCommit.changedFiles.size})"
                                    } else {
                                        changedFilesLabel.text = CommitTracerBundle.message("dialog.changed.files")
                                    }
                                } catch (e: Exception) {
                                    // Log any error but continue
                                    println("Error selecting first commit: ${e.message}")
                                }
                            }
                        }
                    }
                    
                    // Configure columns
                    authorCommitsTable.columnModel.getColumn(0).preferredWidth = 450 // Message
                    authorCommitsTable.columnModel.getColumn(1).preferredWidth = 120 // Date
                    authorCommitsTable.columnModel.getColumn(2).preferredWidth = 80  // Hash
                    authorCommitsTable.columnModel.getColumn(3).preferredWidth = 50  // Tests
                    
                    // Create a custom renderer for the Tests column with green/red icons
                    val testsRenderer = object : DefaultTableCellRenderer() {
                        override fun getTableCellRendererComponent(
                            table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
                        ): Component {
                            val label = super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column) as JLabel
                            label.horizontalAlignment = SwingConstants.CENTER
                            
                            if (value == true) {
                                // Green checkmark for test touches
                                label.text = "✓"
                                label.foreground = JBColor.GREEN
                                label.font = label.font.deriveFont(Font.BOLD)
                            } else {
                                // Red X for no test touches
                                label.text = "✗"
                                label.foreground = JBColor.RED
                            }
                            
                            return label
                        }
                    }
                    authorCommitsTable.columnModel.getColumn(3).cellRenderer = testsRenderer
                    
                    // Add row sorter for author commits table
                    val sorter = TableRowSorter(authorCommitsModel)
                    authorCommitsTable.rowSorter = sorter
                    
                    // Remove previous selection listener if exists
                    authorCommitsSelectionListener?.let {
                        authorCommitsTable.selectionModel.removeListSelectionListener(it)
                    }
                    
                    // Create and add new selection listener to update changed files panel
                    authorCommitsSelectionListener = ListSelectionListener { e ->
                        if (!e.valueIsAdjusting) {
                            val selectedRow = authorCommitsTable.selectedRow
                            if (selectedRow >= 0) {
                                val modelRow = authorCommitsTable.convertRowIndexToModel(selectedRow)
                                val commit = authorCommits[modelRow]
                                
                                // Update the changed files list
                                changedFilesListModel.clear()
                                if (commit.changedFiles.isNotEmpty()) {
                                    // Sort files by path, with test files first
                                    val sortedFiles = commit.changedFiles.sortedWith(
                                        compareByDescending<ChangedFileInfo> { it.isTestFile }
                                        .thenBy { it.path.lowercase() }
                                    )
                                    sortedFiles.forEach { changedFilesListModel.addElement(it) }
                                    changedFilesLabel.text = "${CommitTracerBundle.message("dialog.changed.files")} (${commit.changedFiles.size})"
                                } else {
                                    changedFilesLabel.text = CommitTracerBundle.message("dialog.changed.files")
                                }
                            }
                        }
                    }.also {
                        authorCommitsTable.selectionModel.addListSelectionListener(it)
                    }
                    
                    // Set up filtering on the search field
                    authorCommitsSearchField.document.addDocumentListener(object : DocumentListener {
                        override fun insertUpdate(e: javax.swing.event.DocumentEvent) = filterTable()
                        override fun removeUpdate(e: javax.swing.event.DocumentEvent) = filterTable()
                        override fun changedUpdate(e: javax.swing.event.DocumentEvent) = filterTable()
                        
                        private fun filterTable() {
                            val text = authorCommitsSearchField.text
                            if (text.isNullOrBlank()) {
                                sorter.rowFilter = null
                            } else {
                                try {
                                    // Create case-insensitive regex filter for message column (0)
                                    sorter.rowFilter = RowFilter.regexFilter("(?i)" + text, 0)
                                } catch (ex: java.util.regex.PatternSyntaxException) {
                                    // If the regex pattern is invalid, just show all rows
                                    sorter.rowFilter = null
                                }
                            }
                        }
                    })
                    
                    authorCommitsLabel.text = CommitTracerBundle.message("dialog.author.commits", author.author, authorCommits.size.toString())
                    
                    // Update tickets table
                    val tickets = author.youTrackTickets.entries
                        .map { (ticket, commits) -> 
                            // Check if this ticket is a blocker or regression
                            val isBlocker = author.blockerTickets.containsKey(ticket)
                            val isRegression = author.regressionTickets.containsKey(ticket)
                            TicketInfo(ticket, commits, isBlocker, isRegression)
                        }
                        .toList()
                    
                    val ticketsModel = TicketsTableModel(tickets)
                    ticketsTable.model = ticketsModel
                    
                    // Configure ticket table columns
                    ticketsTable.columnModel.getColumn(0).preferredWidth = 120  // Ticket ID
                    ticketsTable.columnModel.getColumn(1).preferredWidth = 80   // Commit Count
                    ticketsTable.columnModel.getColumn(2).preferredWidth = 80   // Blocker
                    ticketsTable.columnModel.getColumn(3).preferredWidth = 80   // Regression
                    
                    // Center-align numeric columns
                    val centerRenderer = DefaultTableCellRenderer()
                    centerRenderer.horizontalAlignment = SwingConstants.CENTER
                    ticketsTable.columnModel.getColumn(1).cellRenderer = centerRenderer // Commit Count
                    
                    // Boolean renderer for blocker and regression columns
                    ticketsTable.columnModel.getColumn(2).cellRenderer = DefaultTableCellRenderer().apply {
                        horizontalAlignment = SwingConstants.CENTER
                    }
                    ticketsTable.columnModel.getColumn(3).cellRenderer = DefaultTableCellRenderer().apply {
                        horizontalAlignment = SwingConstants.CENTER
                    }
                    
                    // Add row sorter for tickets table
                    val ticketsSorter = TableRowSorter(ticketsModel)
                    
                    // Safely handle different types for commit count
                    ticketsSorter.setComparator(1, Comparator.comparingInt<Any> { 
                        when (it) {
                            is Number -> it.toInt()
                            is String -> it.toString().toIntOrNull() ?: 0
                            else -> 0
                        }
                    })
                    
                    // Safely handle different types for boolean columns
                    ticketsSorter.setComparator(2, Comparator.comparing<Any, Boolean> { 
                        when (it) {
                            is Boolean -> it
                            is String -> it.toString().equals("true", ignoreCase = true)
                            else -> false
                        }
                    })
                    
                    ticketsSorter.setComparator(3, Comparator.comparing<Any, Boolean> { 
                        when (it) {
                            is Boolean -> it
                            is String -> it.toString().equals("true", ignoreCase = true)
                            else -> false
                        }
                    })
                    
                    // Sort by commit count (descending) by default
                    ticketsSorter.toggleSortOrder(1)
                    ticketsSorter.toggleSortOrder(1) // Toggle twice to get descending order
                    
                    ticketsTable.rowSorter = ticketsSorter
                    
                    // Set up filtering on the search field
                    ticketsSearchField.document.addDocumentListener(object : DocumentListener {
                        override fun insertUpdate(e: javax.swing.event.DocumentEvent) = filterTable()
                        override fun removeUpdate(e: javax.swing.event.DocumentEvent) = filterTable()
                        override fun changedUpdate(e: javax.swing.event.DocumentEvent) = filterTable()
                        
                        private fun filterTable() {
                            val text = ticketsSearchField.text
                            if (text.isNullOrBlank()) {
                                ticketsSorter.rowFilter = null
                            } else {
                                try {
                                    // Create case-insensitive regex filter for ticket ID column (0)
                                    ticketsSorter.rowFilter = RowFilter.regexFilter("(?i)" + text, 0)
                                } catch (ex: java.util.regex.PatternSyntaxException) {
                                    // If the regex pattern is invalid, just show all rows
                                    ticketsSorter.rowFilter = null
                                }
                            }
                        }
                    })
                    
                    // Add selection listener to tickets table
                    ticketsTable.selectionModel.addListSelectionListener { ticketEvent ->
                        if (!ticketEvent.valueIsAdjusting) {
                            val selectedTicketRow = ticketsTable.selectedRow
                            if (selectedTicketRow >= 0) {
                                val ticketModelRow = ticketsTable.convertRowIndexToModel(selectedTicketRow)
                                val ticketInfo = tickets[ticketModelRow]
                                
                                // Update author commits table to show only commits related to this ticket
                                val ticketCommitsModel = CommitTableModel(ticketInfo.commits)
                                authorCommitsTable.model = ticketCommitsModel
                                
                                // Clear the changed files panel when ticket selection changes
                                changedFilesListModel.clear()
                                changedFilesLabel.text = CommitTracerBundle.message("dialog.changed.files")
                                
                                // Select the first commit by default if available
                                if (ticketInfo.commits.isNotEmpty()) {
                                    SwingUtilities.invokeLater {
                                        if (authorCommitsTable.rowCount > 0) {
                                            try {
                                                authorCommitsTable.setRowSelectionInterval(0, 0)
                                                authorCommitsTable.scrollRectToVisible(authorCommitsTable.getCellRect(0, 0, true))
                                                
                                                // Manually update the changed files list for the first commit
                                                val selectedCommit = ticketInfo.commits[0]
                                                changedFilesListModel.clear()
                                                if (selectedCommit.changedFiles.isNotEmpty()) {
                                                    // Sort files by path, with test files first
                                                    val sortedFiles = selectedCommit.changedFiles.sortedWith(
                                                        compareByDescending<ChangedFileInfo> { it.isTestFile }
                                                        .thenBy { it.path.lowercase() }
                                                    )
                                                    sortedFiles.forEach { changedFilesListModel.addElement(it) }
                                                    changedFilesLabel.text = "${CommitTracerBundle.message("dialog.changed.files")} (${selectedCommit.changedFiles.size})"
                                                } else {
                                                    changedFilesLabel.text = CommitTracerBundle.message("dialog.changed.files")
                                                }
                                            } catch (e: Exception) {
                                                // Log any error but continue
                                                println("Error selecting first ticket commit: ${e.message}")
                                            }
                                        }
                                    }
                                }
                                
                                // Configure columns
                                authorCommitsTable.columnModel.getColumn(3).preferredWidth = 50  // Tests
                                
                                // Create a custom renderer for the Tests column with green/red icons
                                val testsRenderer = object : DefaultTableCellRenderer() {
                                    override fun getTableCellRendererComponent(
                                        table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
                                    ): Component {
                                        val label = super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column) as JLabel
                                        label.horizontalAlignment = SwingConstants.CENTER
                                        
                                        if (value == true) {
                                            // Green checkmark for test touches
                                            label.text = "✓"
                                            label.foreground = JBColor.GREEN
                                            label.font = label.font.deriveFont(Font.BOLD)
                                        } else {
                                            // Red X for no test touches
                                            label.text = "✗"
                                            label.foreground = JBColor.RED
                                        }
                                        
                                        return label
                                    }
                                }
                                authorCommitsTable.columnModel.getColumn(3).cellRenderer = testsRenderer
                                
                                val ticketCommitsSorter = TableRowSorter(ticketCommitsModel)
                                authorCommitsTable.rowSorter = ticketCommitsSorter
                                
                                // Reset and clear changed files panel
                                changedFilesListModel.clear()
                                changedFilesLabel.text = CommitTracerBundle.message("dialog.changed.files")
                                
                                // Remove previous selection listener if exists
                                ticketCommitsSelectionListener?.let {
                                    authorCommitsTable.selectionModel.removeListSelectionListener(it)
                                }
                                
                                // Create and add selection listener for the ticket-specific commits
                                ticketCommitsSelectionListener = ListSelectionListener { e ->
                                    if (!e.valueIsAdjusting) {
                                        val selectedRow = authorCommitsTable.selectedRow
                                        if (selectedRow >= 0) {
                                            val modelRow = authorCommitsTable.convertRowIndexToModel(selectedRow)
                                            val commit = ticketInfo.commits[modelRow]
                                            
                                            // Update the changed files list
                                            changedFilesListModel.clear()
                                            if (commit.changedFiles.isNotEmpty()) {
                                                // Sort files by path, with test files first
                                                val sortedFiles = commit.changedFiles.sortedWith(
                                                    compareByDescending<ChangedFileInfo> { it.isTestFile }
                                                    .thenBy { it.path.lowercase() }
                                                )
                                                sortedFiles.forEach { changedFilesListModel.addElement(it) }
                                                changedFilesLabel.text = "${CommitTracerBundle.message("dialog.changed.files")} (${commit.changedFiles.size})"
                                            } else {
                                                changedFilesLabel.text = CommitTracerBundle.message("dialog.changed.files")
                                            }
                                        }
                                    }
                                }.also {
                                    authorCommitsTable.selectionModel.addListSelectionListener(it)
                                }
                                
                                // Set up filtering on the author commits search field for ticket commits
                                authorCommitsSearchField.document.addDocumentListener(object : DocumentListener {
                                    override fun insertUpdate(e: javax.swing.event.DocumentEvent) = filterTable()
                                    override fun removeUpdate(e: javax.swing.event.DocumentEvent) = filterTable()
                                    override fun changedUpdate(e: javax.swing.event.DocumentEvent) = filterTable()
                                    
                                    private fun filterTable() {
                                        val text = authorCommitsSearchField.text
                                        if (text.isNullOrBlank()) {
                                            ticketCommitsSorter.rowFilter = null
                                        } else {
                                            try {
                                                // Create case-insensitive regex filter for message column (0)
                                                ticketCommitsSorter.rowFilter = RowFilter.regexFilter("(?i)" + text, 0)
                                            } catch (ex: java.util.regex.PatternSyntaxException) {
                                                // If the regex pattern is invalid, just show all rows
                                                ticketCommitsSorter.rowFilter = null
                                            }
                                        }
                                    }
                                })

                                // Update label
                                authorCommitsLabel.text = CommitTracerBundle.message("dialog.ticket.commits", ticketInfo.ticketId, ticketInfo.commits.size.toString())
                                
                                // Switch to the commits tab
                                authorDetailsTabbedPane.selectedIndex = 0
                            }
                        }
                    }
                    
                    ticketsLabel.text = CommitTracerBundle.message("dialog.youtrack.tickets", tickets.size.toString())
                    ticketsLabel.text = CommitTracerBundle.message("dialog.tickets.for.author", author.author)
                }
            }
        }
        
        // Split view: table on top, details below
        val splitPane2 = OnePixelSplitter(true, 0.5f)
        splitPane2.firstComponent = JBScrollPane(authorsTable)
        splitPane2.secondComponent = authorDetailsTabbedPane
        
        add(splitPane2, BorderLayout.CENTER)
        
        // Select first row by default if there are authors
        if (authorStats.isNotEmpty()) {
            authorsTable.selectionModel.setSelectionInterval(0, 0)
        }
        
        // Set initial statistics in the title
        updateFilteredStatistics()
    }
    
    /**
     * Update the table with new data
     */
    fun updateData(newAuthors: List<AuthorStats>) {
        (authorsTable.model as AuthorTableModel).updateData(newAuthors)
        
        // Update statistics to reflect new data
        updateFilteredStatistics()
    }
    
    /**
     * Select the first author in the list
     */
    fun selectFirstAuthor() {
        if (authorsTable.rowCount > 0) {
            authorsTable.selectionModel.setSelectionInterval(0, 0)
            authorsTable.scrollRectToVisible(authorsTable.getCellRect(0, 0, true))
        }
    }
    
    /**
     * Filter the authors table with a text filter
     * This allows the parent dialog to control filtering
     */
    fun filterByText(text: String) {
        val sorter = authorsTable.rowSorter as TableRowSorter<*>
        
        if (text.isBlank()) {
            sorter.rowFilter = null
        } else {
            @Suppress("UNCHECKED_CAST")
            sorter.rowFilter = createSearchFilter(text) as RowFilter<Any, Int>
        }
        
        // Update statistics to reflect the filtered data
        updateFilteredStatistics()
    }
    
    
    /**
     * Updates statistics based on currently visible rows
     */
    private fun updateFilteredStatistics() {
        // Get visible row count
        val visibleRowCount = authorsTable.rowCount
        
        // Calculate totals from visible rows
        var totalCommits = 0
        var totalTickets = 0
        var totalBlockers = 0
        var totalRegressions = 0
        
        // Collect visible commit counts for median calculation
        val visibleCommitCounts = mutableListOf<Int>()
        
        for (viewRow in 0 until visibleRowCount) {
            val modelRow = authorsTable.convertRowIndexToModel(viewRow)
            val author = authorStats.values.toList()[modelRow]
            
            totalCommits += author.commitCount
            totalTickets += author.youTrackTickets.size
            totalBlockers += author.blockerTickets.size
            totalRegressions += author.regressionTickets.size
            
            // Add to list for median calculation
            visibleCommitCounts.add(author.commitCount)
        }
        
        // Calculate average commits per author
        val avgCommitsPerAuthor = if (visibleRowCount > 0) {
            String.format("%.2f", totalCommits.toDouble() / visibleRowCount)
        } else {
            "0.00"
        }
        
        // Calculate median commits per author
        val medianCommitsPerAuthor = if (visibleCommitCounts.isNotEmpty()) {
            val sortedCounts = visibleCommitCounts.sorted()
            val middle = sortedCounts.size / 2
            
            if (sortedCounts.size % 2 == 0) {
                // Even number of elements, average the middle two
                val median = (sortedCounts[middle - 1] + sortedCounts[middle]) / 2.0
                String.format("%.1f", median)
            } else {
                // Odd number of elements, take the middle one
                sortedCounts[middle].toString()
            }
        } else {
            "0"
        }
        
        // Use callback to update parent dialog statistics if provided
        updateStatsCallback?.invoke(
            visibleRowCount, 
            totalCommits, 
            totalTickets, 
            avgCommitsPerAuthor, 
            medianCommitsPerAuthor
        )
    }
    
    /**
     * Creates a row filter that matches against email, name, team, and title
     */
    private fun createSearchFilter(text: String): RowFilter<Any, Any> {
        return object : RowFilter<Any, Any>() {
            override fun include(entry: Entry<out Any, out Any>): Boolean {
                if (text.isBlank()) return true
                
                val searchTerms = text.lowercase()
                
                // First check author email (column 0)
                val authorEmail = entry.getStringValue(0).lowercase()
                if (authorEmail.contains(searchTerms)) return true
                
                // Check additional fields if they exist (name, team, title)
                try {
                    // Check name (column 4) if available
                    val authorName = entry.getStringValue(4).lowercase()
                    if (authorName.contains(searchTerms)) return true
                    
                    // Check team (column 5) if available
                    val teamName = entry.getStringValue(5).lowercase()
                    if (teamName.contains(searchTerms)) return true
                    
                    // Check title (column 6) if available
                    val title = entry.getStringValue(6).lowercase()
                    if (title.contains(searchTerms)) return true
                } catch (ex: Exception) {
                    // If columns don't exist or there's any error, we've already checked email
                }
                
                return false
            }
        }
    }
}