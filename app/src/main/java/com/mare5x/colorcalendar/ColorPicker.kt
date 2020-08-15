package com.mare5x.colorcalendar

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.DialogFragment


// Use either as a dialog or as a fragment.
class ColorPickerDialogFragment : DialogFragment() {
    private lateinit var colorRect: ColorRect
    private lateinit var bar: SeekBar

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.color_picker_view, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        colorRect = view.findViewById(R.id.colorRect)
        bar = view.findViewById<ColorSeekBar>(R.id.colorSeekBar)
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) =
                updateColorRect(progress)
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        updateColorRect(bar.progress)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        return dialog
    }

    fun updateColorRect(progress: Int) {
        val x = progress / bar.max.toFloat()
        colorRect.color = calcGradientColor(Color.RED, Color.GREEN, x)
    }
}