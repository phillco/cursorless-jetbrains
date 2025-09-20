package com.github.asoee.cursorlessjetbrains.effects

import com.github.asoee.cursorlessjetbrains.settings.TalonSettings
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.util.TextRange
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Point
import java.awt.RenderingHints
import java.util.concurrent.ConcurrentLinkedQueue
import javax.swing.Timer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class Particle(
    var x: Double,
    var y: Double,
    var vx: Double,
    var vy: Double,
    var life: Float,
    val color: Color,
    val character: String,
    val size: Float = 1.0f
)

class ParticleEffect(private val editor: Editor) {
    private val particles = ConcurrentLinkedQueue<Particle>()
    private var animationTimer: Timer? = null
    private val random = Random(System.currentTimeMillis())
    private val settings = TalonSettings.instance.state
    
    // Style configurations
    private val styleConfigs = mapOf(
        TalonSettings.ParticleStyle.SPARKLES to StyleConfig(
            characters = listOf("✨", "•", "◦", "°", "∘", "⋅", "·", "▪", "▫"),
            colors = listOf(
                Color(255, 107, 107), Color(255, 193, 7), Color(76, 175, 80),
                Color(33, 150, 243), Color(156, 39, 176), Color(255, 152, 0),
                Color(0, 188, 212), Color(233, 30, 99)
            ),
            gravity = 150.0,
            spread = 0.5,
            speedMultiplier = 1.0
        ),
        TalonSettings.ParticleStyle.FIRE to StyleConfig(
            characters = listOf("🔥", "◆", "◇", "▲", "△"),
            colors = listOf(
                Color(255, 69, 0), Color(255, 140, 0), Color(255, 215, 0),
                Color(255, 255, 0), Color(255, 99, 71)
            ),
            gravity = -200.0, // negative for upward
            spread = 0.3,
            speedMultiplier = 1.5
        ),
        TalonSettings.ParticleStyle.SMOKE to StyleConfig(
            characters = listOf("○", "◯", "●", "◉", "◌"),
            colors = listOf(
                Color(128, 128, 128), Color(169, 169, 169), Color(192, 192, 192),
                Color(211, 211, 211), Color(220, 220, 220)
            ),
            gravity = -50.0,
            spread = 0.8,
            speedMultiplier = 0.5
        ),
        TalonSettings.ParticleStyle.MATRIX to StyleConfig(
            characters = "ｦｧｨｩｪｫｬｭｮｯｰｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾖﾗﾘﾙﾚﾛﾜﾝ0123456789".map { it.toString() },
            colors = listOf(Color(0, 255, 0), Color(0, 200, 0), Color(0, 150, 0)),
            gravity = 300.0,
            spread = 0.1,
            speedMultiplier = 2.0
        ),
        TalonSettings.ParticleStyle.CONFETTI to StyleConfig(
            characters = listOf("■", "▪", "▫", "◼", "◻"),
            colors = listOf(
                Color(255, 0, 0), Color(0, 255, 0), Color(0, 0, 255),
                Color(255, 255, 0), Color(255, 0, 255), Color(0, 255, 255)
            ),
            gravity = 100.0,
            spread = 1.0,
            speedMultiplier = 0.8
        ),
        TalonSettings.ParticleStyle.SNOW to StyleConfig(
            characters = listOf("❄", "❅", "❆", "•", "◦"),
            colors = listOf(Color(255, 255, 255), Color(240, 248, 255), Color(248, 248, 255)),
            gravity = 50.0,
            spread = 0.7,
            speedMultiplier = 0.3
        )
    )
    
    private data class StyleConfig(
        val characters: List<String>,
        val colors: List<Color>,
        val gravity: Double,
        val spread: Double,
        val speedMultiplier: Double
    )
    
    fun emitParticlesForDeletedText(textRange: TextRange, deletedText: String) {
        if (!settings.enableParticleEffects) return
        
        val startPoint = editor.offsetToXY(textRange.startOffset)
        val endPoint = editor.offsetToXY(textRange.endOffset)
        
        // Calculate particle count based on text length and intensity setting
        val intensityMultiplier = settings.particleIntensity / 100.0
        val baseParticles = deletedText.length * 3
        val particleCount = (baseParticles * intensityMultiplier).toInt().coerceIn(1, 100)
        
        // Distribute particles along the deleted text range
        for (i in 0 until particleCount) {
            val progress = if (particleCount > 1) i.toFloat() / (particleCount - 1) else 0.5f
            val x = startPoint.x + (endPoint.x - startPoint.x) * progress
            val y = startPoint.y + (endPoint.y - startPoint.y) * progress
            
            emitParticle(x.toDouble(), y.toDouble())
        }
        
        // Start animation if not already running
        if (animationTimer == null) {
            startAnimation()
        }
    }
    
    private fun emitParticle(x: Double, y: Double) {
        val config = styleConfigs[settings.particleStyle] ?: styleConfigs[TalonSettings.ParticleStyle.SPARKLES]!!
        
        // Random angle within spread
        val angle = when (settings.particleStyle) {
            TalonSettings.ParticleStyle.MATRIX, TalonSettings.ParticleStyle.SNOW -> 
                Math.PI / 2 + (random.nextDouble() - 0.5) * config.spread // downward
            else -> -Math.PI / 2 + (random.nextDouble() - 0.5) * config.spread // upward
        }
        
        val baseSpeed = 100.0
        val speed = baseSpeed * config.speedMultiplier * (0.5 + random.nextDouble() * 0.5)
        
        val particle = Particle(
            x = x + random.nextDouble() * 10 - 5,
            y = y + random.nextDouble() * 10 - 5,
            vx = cos(angle) * speed,
            vy = sin(angle) * speed,
            life = settings.particleLifetime / 1000f, // convert to seconds
            color = config.colors.random(random),
            character = config.characters.random(random),
            size = 0.8f + random.nextFloat() * 0.4f
        )
        
        particles.offer(particle)
    }
    
    private fun startAnimation() {
        animationTimer = Timer(16) { // ~60 FPS
            updateParticles()
            if (particles.isEmpty()) {
                stopAnimation()
            } else {
                editor.component.repaint()
            }
        }
        animationTimer?.start()
    }
    
    private fun stopAnimation() {
        animationTimer?.stop()
        animationTimer = null
        editor.component.repaint()
    }
    
    private fun updateParticles() {
        val deltaTime = 0.016f // 16ms per frame
        val iterator = particles.iterator()
        val config = styleConfigs[settings.particleStyle] ?: styleConfigs[TalonSettings.ParticleStyle.SPARKLES]!!
        
        while (iterator.hasNext()) {
            val particle = iterator.next()
            
            // Update position
            particle.x += particle.vx * deltaTime
            particle.y += particle.vy * deltaTime
            
            // Apply gravity if enabled
            if (settings.particleGravity) {
                particle.vy += config.gravity * deltaTime
            }
            
            // Update life
            particle.life -= deltaTime
            
            // Remove dead particles
            if (particle.life <= 0) {
                iterator.remove()
            }
        }
    }
    
    fun paintParticles(g: Graphics2D, visibleArea: java.awt.Rectangle) {
        if (particles.isEmpty()) return
        
        val originalHints = g.getRenderingHints()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        
        val font = editor.colorsScheme.getFont(EditorFontType.PLAIN)
        val metrics = g.getFontMetrics(font)
        
        particles.forEach { particle ->
            // Skip particles outside visible area
            if (particle.x < visibleArea.x - 50 || particle.x > visibleArea.x + visibleArea.width + 50 ||
                particle.y < visibleArea.y - 50 || particle.y > visibleArea.y + visibleArea.height + 50) {
                return@forEach
            }
            
            // Calculate alpha based on life
            val maxLife = settings.particleLifetime / 1000f
            val alpha = (particle.life / maxLife * 255).toInt().coerceIn(0, 255)
            val color = Color(particle.color.red, particle.color.green, particle.color.blue, alpha)
            
            g.color = color
            g.font = font.deriveFont(font.size * particle.size)
            
            // Draw particle
            val charWidth = metrics.charWidth(particle.character[0])
            val charHeight = metrics.height
            g.drawString(
                particle.character,
                particle.x.toInt() - charWidth / 2,
                particle.y.toInt() + charHeight / 4
            )
        }
        
        g.setRenderingHints(originalHints)
    }
    
    fun dispose() {
        stopAnimation()
        particles.clear()
    }
}