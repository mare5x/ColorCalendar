package com.mare5x.colorcalendar

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels


// Use either as a dialog or as a fragment.
// Note: Dialog's use wrap_content for layout width and height ...
class ColorPickerDialogFragment : DialogFragment() {
    // NOTE: the parent must implement this interface ...
    // Using function callbacks doesn't work because of configuration changes ...!
    interface ColorPickerListener {
        fun onColorConfirm(value: Float)
        fun onColorCancel(value: Float)
    }

    private var listener: ColorPickerListener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_color_picker, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val colorPickerBar = view.findViewById<ColorPickerBar>(R.id.colorPickerBar)
        val mainModel: MainViewModel by activityViewModels()
        val profile = mainModel.getCurrentProfile().value
        if (profile != null)
            colorPickerBar.setColors(profile.minColor, profile.maxColor)

        val cancelButton = view.findViewById<Button>(R.id.cancelButton)
        cancelButton.setOnClickListener {
            listener?.onColorCancel(colorPickerBar.getNormProgress())
            if (showsDialog)
                dismiss()
        }
        val confirmButton = view.findViewById<Button>(R.id.confirmButton)
        confirmButton.setOnClickListener {
            listener?.onColorConfirm(colorPickerBar.getNormProgress())
            if (showsDialog)
                dismiss()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState)
    }

    /*
    override fun onStart() {
        super.onStart()
        // Work-around to get a bigger dialog ...
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
     */

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? ColorPickerListener
    }
}