package com.example.ijcommittracer.ui.components

import com.example.ijcommittracer.CommitTracerBundle
import com.example.ijcommittracer.ui.util.JDateChooser
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.LoadingDecorator
import com.intellij.ui.components.JBLabel
import java.awt.Dimension
import java.awt.FlowLayout
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.JButton
import javax.swing.JPanel

/**
 * Panel for date range filtering.
 */
class DateFilterPanel(private val fromDate: Date, private val toDate: Date, private val onFilterApplied: (Date, Date) -> Unit) : JPanel(FlowLayout(FlowLayout.RIGHT)), Disposable {
    
    private val displayDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    lateinit var fromDatePicker: JDateChooser
    lateinit var toDatePicker: JDateChooser
    lateinit var filterButton: JButton
    private lateinit var loadingDecorator: LoadingDecorator
    private lateinit var buttonPanel: JPanel
    
    init {
        initialize()
    }
    
    private fun initialize() {
        add(JBLabel(CommitTracerBundle.message("dialog.filter.from")))
        fromDatePicker = JDateChooser(fromDate, displayDateFormat)
        fromDatePicker.preferredSize = Dimension(120, 30)
        add(fromDatePicker)
        
        add(JBLabel(CommitTracerBundle.message("dialog.filter.to")))
        toDatePicker = JDateChooser(toDate, displayDateFormat)
        toDatePicker.preferredSize = Dimension(120, 30)
        add(toDatePicker)
        
        // Create button
        filterButton = JButton(CommitTracerBundle.message("dialog.filter.apply"))
        filterButton.addActionListener { 
            val newFromDate = fromDatePicker.date
            val newToDate = toDatePicker.date
            
            if (newFromDate != null && newToDate != null) {
                // Show loading spinner
                startLoading()
                
                // Apply filter
                onFilterApplied(newFromDate, newToDate)
            }
        }
        
        // Create panel for button with loading spinner
        buttonPanel = JPanel(FlowLayout(FlowLayout.CENTER, 0, 0))
        buttonPanel.add(filterButton)
        
        // Create loading decorator with spinner
        loadingDecorator = LoadingDecorator(buttonPanel, this, 0)
        
        // Add the decorated button panel
        add(loadingDecorator.component)
    }
    
    /**
     * Start the loading animation
     */
    fun startLoading() {
        loadingDecorator.startLoading(false)
        filterButton.isEnabled = false
    }
    
    /**
     * Stop the loading animation
     */
    fun stopLoading() {
        loadingDecorator.stopLoading()
        filterButton.isEnabled = true
    }
    
    /**
     * Dispose of resources
     */
    override fun dispose() {
        // No resources to clean up
    }
}