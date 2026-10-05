package com.sudoflash01.edgeaction

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.view.View
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.ImageView
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.core.graphics.ColorUtils
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.R as MR

// small animation helpers
object Anim {
    val emphasized = PathInterpolator(0.2f, 0f, 0f, 1f)
    val decelerate = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    val accelerate = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)

    fun color(v: View, @AttrRes attr: Int): Int = MaterialColors.getColor(v, attr)

    // change icon with animation. old icon shrinks, new one pops in.
    // first call only sets the icon, no animation (screen is still loading)
    fun swapIcon(iv: ImageView, @DrawableRes res: Int, animate: Boolean = true) {
        val cur = iv.getTag(R.id.tag_icon) as? Int
        if (cur == res) return
        iv.setTag(R.id.tag_icon, res)
        iv.animate().cancel()
        if (cur == null || !animate || !iv.isShown) {
            iv.setImageResource(res)
            iv.scaleX = 1f; iv.scaleY = 1f; iv.rotation = 0f; iv.alpha = 1f
            return
        }
        iv.animate()
            .scaleX(0f).scaleY(0f).rotation(-60f).alpha(0f)
            .setDuration(90).setInterpolator(accelerate)
            .withEndAction {
                iv.setImageResource(res)
                iv.rotation = 60f
                iv.animate()
                    .scaleX(1f).scaleY(1f).rotation(0f).alpha(1f)
                    .setDuration(300).setInterpolator(OvershootInterpolator(2.2f))
                    .start()
            }
            .start()
    }

    // round bg of the icon. off = tonal secondary, on = primary container
    fun leading(iv: ImageView, on: Boolean, animate: Boolean = true) {
        val bgTo = color(iv, if (on) MR.attr.colorPrimaryContainer else MR.attr.colorSecondaryContainer)
        val fgTo = color(iv, if (on) MR.attr.colorOnPrimaryContainer else MR.attr.colorOnSecondaryContainer)
        (iv.getTag(R.id.tag_anim) as? ValueAnimator)?.cancel()
        val bgFrom = iv.backgroundTintList?.defaultColor ?: color(iv, MR.attr.colorSecondaryContainer)
        val fgFrom = iv.imageTintList?.defaultColor ?: color(iv, MR.attr.colorOnSecondaryContainer)
        fun set(b: Int, f: Int) {
            iv.backgroundTintList = ColorStateList.valueOf(b)
            iv.imageTintList = ColorStateList.valueOf(f)
        }
        if (!animate || !iv.isShown || (bgFrom == bgTo && fgFrom == fgTo)) { set(bgTo, fgTo); return }
        val a = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            interpolator = emphasized
            addUpdateListener {
                val t = it.animatedFraction
                set(ColorUtils.blendARGB(bgFrom, bgTo, t), ColorUtils.blendARGB(fgFrom, fgTo, t))
            }
        }
        iv.setTag(R.id.tag_anim, a)
        a.start()
    }

    // smooth change of card background colour
    fun tintCard(card: MaterialCardView, to: Int, animate: Boolean = true) {
        (card.getTag(R.id.tag_anim) as? ValueAnimator)?.cancel()
        val from = card.cardBackgroundColor.defaultColor
        if (!animate || !card.isShown || from == to) { card.setCardBackgroundColor(to); return }
        val a = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 350
            interpolator = emphasized
            addUpdateListener { card.setCardBackgroundColor(ColorUtils.blendARGB(from, to, it.animatedFraction)) }
        }
        card.setTag(R.id.tag_anim, a)
        a.start()
    }

    // small bounce when toggle changes
    fun pop(v: View) {
        v.animate().cancel()
        v.scaleX = 0.78f
        v.scaleY = 0.78f
        v.animate().scaleX(1f).scaleY(1f).setDuration(340).setInterpolator(OvershootInterpolator(3f)).start()
    }

    fun fade(v: View, to: Float, duration: Long = 250) {
        v.animate().alpha(to).setDuration(duration).setInterpolator(emphasized).start()
    }

    // slide up + fade in, one by one (index = position in list)
    fun enter(v: View, index: Int) {
        val d = v.resources.displayMetrics.density
        v.animate().cancel()
        v.alpha = 0f
        v.translationY = 28 * d
        v.animate().alpha(1f).translationY(0f)
            .setStartDelay(index * 45L).setDuration(420).setInterpolator(decelerate).start()
    }

    // corner sizes for grouped list. outer corners big, inside small
    fun segment(card: MaterialCardView, index: Int, count: Int) {
        val d = card.resources.displayMetrics.density
        val big = 28 * d
        val small = 6 * d
        val top = if (index == 0) big else small
        val bottom = if (index == count - 1) big else small
        card.shapeAppearanceModel = ShapeAppearanceModel.builder()
            .setTopLeftCorner(CornerFamily.ROUNDED, top)
            .setTopRightCorner(CornerFamily.ROUNDED, top)
            .setBottomLeftCorner(CornerFamily.ROUNDED, bottom)
            .setBottomRightCorner(CornerFamily.ROUNDED, bottom)
            .build()
    }
}
