package com.github.asoee.cursorlessjetbrains.effects

import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.markup.CustomHighlighterLayer
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle

class ParticleEffectPainter(private val particleEffect: ParticleEffect) : CustomHighlighterRenderer {
    
    companion object {
        // Custom layer for particle effects - rendered above text but below other decorations
        const val PARTICLE_LAYER = CustomHighlighterLayer.SELECTION + 1000
    }
    
    override fun paint(
        editor: com.intellij.openapi.editor.Editor,
        highlighter: RangeHighlighter,
        g: Graphics
    ) {
        val g2d = g as Graphics2D
        val visibleArea = editor.scrollingModel.visibleArea
        
        // Paint all particles
        particleEffect.paintParticles(g2d, visibleArea)
    }
}