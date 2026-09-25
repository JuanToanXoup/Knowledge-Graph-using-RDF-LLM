package io.github.juantoanxoup.kg.web.components.ui

import react.FC
import react.Props
import react.dom.html.ReactHTML.input
import web.cssom.ClassName
import web.html.InputType
import web.html.range

external interface SliderProps : Props {
    var value: Int
    var min: Int
    var max: Int
    var step: Int
    var onValueChange: ((Int) -> Unit)?
}

/** shadcn `Slider` equivalent backed by a native range input. */
val Slider =
    FC<SliderProps> { props ->
        input {
            className = ClassName("range")
            type = InputType.range
            min = props.min
            max = props.max
            step = props.step.toDouble()
            value = props.value.toString()
            onChange = { event -> props.onValueChange?.invoke(event.target.value.toIntOrNull() ?: props.value) }
        }
    }
