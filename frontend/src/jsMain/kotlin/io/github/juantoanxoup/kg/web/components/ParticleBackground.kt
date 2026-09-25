package io.github.juantoanxoup.kg.web.components

import js.reflect.unsafeCast
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import react.FC
import react.Props
import react.dom.html.ReactHTML.canvas
import react.useEffectOnce
import react.useRef
import web.animations.FrameRequestId
import web.animations.cancelAnimationFrame
import web.animations.requestAnimationFrame
import web.canvas.CanvasRenderingContext2D
import web.canvas.ID
import web.cssom.ClassName
import web.events.invoke
import web.html.HTMLCanvasElement
import web.mouse.MouseEvent
import web.window.mouseMoveEvent
import web.window.resizeEvent
import web.window.window
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private class Particle(
    var x: Double,
    var y: Double,
    var vx: Double,
    var vy: Double,
    val opacity: Double,
    var pulsePhase: Double,
)

/** Animated ember particle field with mouse parallax (src/components/ParticleBackground.tsx). */
val ParticleBackground =
    FC<Props> {
        val canvasRef = useRef<HTMLCanvasElement>()

        useEffectOnce {
            val canvas = canvasRef.current ?: return@useEffectOnce
            val ctx = canvas.getContext(CanvasRenderingContext2D.ID) ?: return@useEffectOnce

            fun resize() {
                canvas.width = window.innerWidth
                canvas.height = window.innerHeight
            }
            resize()

            val particleCount = if (window.innerWidth < 768) 30 else 60
            val particles =
                List(particleCount) {
                    Particle(
                        x = Random.nextDouble() * canvas.width,
                        y = Random.nextDouble() * canvas.height,
                        vx = (Random.nextDouble() - 0.5) * 0.3,
                        vy = (Random.nextDouble() - 0.5) * 0.3,
                        opacity = Random.nextDouble() * 0.5 + 0.3,
                        pulsePhase = Random.nextDouble() * PI * 2,
                    )
                }
            var mouseX = 0.0
            var mouseY = 0.0
            var frame: FrameRequestId? = null

            launch { window.resizeEvent().collect { resize() } }
            launch {
                window.mouseMoveEvent().collect { event: MouseEvent ->
                    mouseX = event.clientX.toDouble()
                    mouseY = event.clientY.toDouble()
                }
            }

            fun animate() {
                val width = canvas.width.toDouble()
                val height = canvas.height.toDouble()
                ctx.clearRect(0.0, 0.0, width, height)

                particles.forEachIndexed { i, particle ->
                    particle.x += particle.vx
                    particle.y += particle.vy
                    if (particle.x < 0 || particle.x > width) particle.vx *= -1
                    if (particle.y < 0 || particle.y > height) particle.vy *= -1

                    particle.pulsePhase += 0.02
                    val pulse = sin(particle.pulsePhase) * 0.2 + 0.8

                    val dx = (mouseX - particle.x) * 0.00005
                    val dy = (mouseY - particle.y) * 0.00005
                    val px = particle.x + dx * 50
                    val py = particle.y + dy * 50
                    val radius = 8 * pulse

                    val gradient = ctx.createRadialGradient(px, py, 0.0, px, py, radius)
                    gradient.addColorStop(0.0, "rgba(255, 106, 0, ${particle.opacity * pulse})")
                    gradient.addColorStop(0.5, "rgba(255, 159, 67, ${particle.opacity * pulse * 0.5})")
                    gradient.addColorStop(1.0, "rgba(255, 106, 0, 0)")
                    ctx.fillStyle = gradient
                    ctx.beginPath()
                    ctx.arc(px, py, radius, 0.0, PI * 2)
                    ctx.fill()

                    particles.forEachIndexed { j, other ->
                        if (i == j) return@forEachIndexed
                        val distance = hypot(particle.x - other.x, particle.y - other.y)
                        if (distance < 150) {
                            val opacity = (1 - distance / 150) * 0.2
                            ctx.strokeStyle = unsafeCast<JsAny>("rgba(255, 106, 0, $opacity)")
                            ctx.lineWidth = 0.5
                            ctx.beginPath()
                            ctx.moveTo(particle.x, particle.y)
                            ctx.lineTo(other.x, other.y)
                            ctx.stroke()
                        }
                    }
                }
                frame = requestAnimationFrame { animate() }
            }
            animate()

            try {
                awaitCancellation()
            } finally {
                frame?.let { cancelAnimationFrame(it) }
            }
        }

        canvas {
            ref = canvasRef
            className = ClassName("particles")
        }
    }
