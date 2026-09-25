@file:JsModule("lucide-react")
@file:JsNonModule

package io.github.juantoanxoup.kg.web.lib

import react.FC
import react.PropsWithClassName

/** Props accepted by every lucide icon. */
external interface IconProps : PropsWithClassName {
    var size: Int?
    var strokeWidth: Double?
}

external val Brain: FC<IconProps>
external val LayoutDashboard: FC<IconProps>
external val Home: FC<IconProps>
external val Upload: FC<IconProps>
external val Zap: FC<IconProps>
external val Lock: FC<IconProps>
external val Search: FC<IconProps>
external val Link2: FC<IconProps>
external val MessageSquare: FC<IconProps>
external val Cpu: FC<IconProps>
external val Eye: FC<IconProps>
external val Mail: FC<IconProps>
external val BarChart3: FC<IconProps>
external val Network: FC<IconProps>
external val Orbit: FC<IconProps>
external val Code: FC<IconProps>
external val FolderOpen: FC<IconProps>
external val Settings: FC<IconProps>
external val ChevronLeft: FC<IconProps>
external val ChevronRight: FC<IconProps>
external val Database: FC<IconProps>
external val Layers: FC<IconProps>
external val Play: FC<IconProps>
external val Trash2: FC<IconProps>
external val Download: FC<IconProps>
external val Send: FC<IconProps>
external val Copy: FC<IconProps>
external val ThumbsUp: FC<IconProps>
external val ThumbsDown: FC<IconProps>
