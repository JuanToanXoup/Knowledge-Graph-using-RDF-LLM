package io.github.juantoanxoup.kg.cloud.components

import io.github.juantoanxoup.kg.cloud.CloudRuntime
import react.Props

external interface RuntimeProps : Props {
    var runtime: CloudRuntime
}
