/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.animation;

import javafx.animation.Interpolator;
import javafx.util.Duration;

import java.util.Objects;

/// @author Glavo
/// @see <a href="https://api.flutter.dev/flutter/animation/Curves-class.html">Flutter Curves</a>
public final class Motion {

    //region Curves

    /// A linear animation curve.
    ///
    /// This is the identity map over the unit interval: its [Interpolator#curve(double)]
    /// method returns its input unmodified. This is useful as a default curve for
    /// cases where a [Interpolator] is required but no actual curve is desired.
    ///
    /// @see <a href="https://flutter.github.io/assets-for-api-docs/assets/animation/curve_linear.mp4">curve_linear.mp4</a>
    public static final Interpolator LINEAR = Interpolator.LINEAR;

    /// The emphasizedAccelerate easing curve in the Material specification.
    ///
    /// See also:
    ///
    /// * [M3 guidelines: Easing tokens](https://m3.material.io/styles/motion/easing-and-duration/tokens-specs#433b1153-2ea3-4fe2-9748-803a47bc97ee)
    /// * [M3 guidelines: Applying easing and duration](https://m3.material.io/styles/motion/easing-and-duration/applying-easing-and-duration)
    public static final Interpolator EMPHASIZED_ACCELERATE = new Cubic(0.3, 0.0, 0.8, 0.15);

    /// The emphasizedDecelerate easing curve in the Material specification.
    ///
    /// See also:
    ///
    /// * [M3 guidelines: Easing tokens](https://m3.material.io/styles/motion/easing-and-duration/tokens-specs#433b1153-2ea3-4fe2-9748-803a47bc97ee)
    /// * [M3 guidelines: Applying easing and duration](https://m3.material.io/styles/motion/easing-and-duration/applying-easing-and-duration)
    public static final Interpolator EMPHASIZED_DECELERATE = new Cubic(0.05, 0.7, 0.1, 1.0);

    /// The standard easing curve in the Material specification.
    ///
    /// See also:
    ///
    /// * [M3 guidelines: Easing tokens](https://m3.material.io/styles/motion/easing-and-duration/tokens-specs#433b1153-2ea3-4fe2-9748-803a47bc97ee)
    /// * [M3 guidelines: Applying easing and duration](https://m3.material.io/styles/motion/easing-and-duration/applying-easing-and-duration)
    public static final Interpolator STANDARD = new Cubic(0.2, 0.0, 0.0, 1.0);

    /// The standardAccelerate easing curve in the Material specification.
    ///
    /// See also:
    ///
    /// * [M3 guidelines: Easing tokens](https://m3.material.io/styles/motion/easing-and-duration/tokens-specs#433b1153-2ea3-4fe2-9748-803a47bc97ee)
    /// * [M3 guidelines: Applying easing and duration](https://m3.material.io/styles/motion/easing-and-duration/applying-easing-and-duration)
    public static final Interpolator STANDARD_ACCELERATE = new Cubic(0.3, 0.0, 1.0, 1.0);

    /// The standardDecelerate easing curve in the Material specification.
    ///
    /// See also:
    ///
    /// * [M3 guidelines: Easing tokens](https://m3.material.io/styles/motion/easing-and-duration/tokens-specs#433b1153-2ea3-4fe2-9748-803a47bc97ee)
    /// * [M3 guidelines: Applying easing and duration](https://m3.material.io/styles/motion/easing-and-duration/applying-easing-and-duration)
    public static Interpolator STANDARD_DECELERATE = new Cubic(0.0, 0.0, 0.0, 1.0);

    /// The legacyDecelerate easing curve in the Material specification.
    ///
    /// See also:
    ///
    /// * [M3 guidelines: Easing tokens](https://m3.material.io/styles/motion/easing-and-duration/tokens-specs#433b1153-2ea3-4fe2-9748-803a47bc97ee)
    /// * [M3 guidelines: Applying easing and duration](https://m3.material.io/styles/motion/easing-and-duration/applying-easing-and-duration)
    public static Interpolator LEGACY_DECELERATE = new Cubic(0.0, 0.0, 0.2, 1.0);

    /// The legacyAccelerate easing curve in the Material specification.
    ///
    /// See also:
    ///
    /// * [M3 guidelines: Easing tokens](https://m3.material.io/styles/motion/easing-and-duration/tokens-specs#433b1153-2ea3-4fe2-9748-803a47bc97ee)
    /// * [M3 guidelines: Applying easing and duration](https://m3.material.io/styles/motion/easing-and-duration/applying-easing-and-duration)
    public static Interpolator LEGACY_ACCELERATE = new Cubic(0.4, 0.0, 1.0, 1.0);

    /// The legacy easing curve in the Material specification.
    ///
    /// See also:
    ///
    /// * [M3 guidelines: Easing tokens](https://m3.material.io/styles/motion/easing-and-duration/tokens-specs#433b1153-2ea3-4fe2-9748-803a47bc97ee)
    /// * [M3 guidelines: Applying easing and duration](https://m3.material.io/styles/motion/easing-and-duration/applying-easing-and-duration)
    public static Interpolator LEGACY = new Cubic(0.4, 0.0, 0.2, 1.0);

    /// A cubic animation curve that speeds up quickly and ends slowly.
    ///
    /// This is the same as the CSS easing function `ease`.
    ///
    /// @see <a href="https://flutter.github.io/assets-for-api-docs/assets/animation/curve_ease.mp4">curve_ease.mp4</a>
    public static final Interpolator EASE = new Cubic(0.25, 0.1, 0.25, 1.0);

    /// A cubic animation curve that starts slowly and ends quickly.
    ///
    /// This is the same as the CSS easing function `ease-in`.
    ///
    /// @see <a href="https://flutter.github.io/assets-for-api-docs/assets/animation/curve_ease_in.mp4">curve_ease_in.mp4</a>
    public static final Interpolator EASE_IN = new Cubic(0.42, 0.0, 1.0, 1.0);

    /// A cubic animation curve that starts slowly and ends linearly.
    ///
    /// The symmetric animation to [#LINEAR_TO_EASE_OUT].
    ///
    /// @see <a href="https://flutter.github.io/assets-for-api-docs/assets/animation/curve_ease_in_to_linear.mp4">curve_ease_in_to_linear.mp4</a>
    public static final Interpolator EASE_IN_TO_LINEAR = new Cubic(0.67, 0.03, 0.65, 0.09);

    /// A cubic animation curve that starts slowly and ends quickly. This is
    /// similar to [#EASE_IN], but with sinusoidal easing for a slightly less
    /// abrupt beginning and end. Nonetheless, the result is quite gentle and is
    /// hard to distinguish from [#linear] at a glance.
    ///
    /// Derived from Robert Penner’s easing functions.
    ///
    /// @see <a href="https://flutter.github.io/assets-for-api-docs/assets/animation/curve_ease_in_sine.mp4">curve_ease_in_sine.mp4</a>
    public static final Interpolator EASE_IN_SINE = new Cubic(0.47, 0.0, 0.745, 0.715);

    /// A cubic animation curve that starts slowly and ends quickly. Based on a
    /// quadratic equation where `f(t) = t²`, this is effectively the inverse of
    /// [#decelerate].
    ///
    /// Compared to [#EASE_IN_SINE], this curve is slightly steeper.
    ///
    /// Derived from Robert Penner’s easing functions.
    ///
    /// @see <a href="https://flutter.github.io/assets-for-api-docs/assets/animation/curve_ease_in_quad.mp4">curve_ease_in_quad.mp4</a>
    public static final Interpolator EASE_IN_QUAD = new Cubic(0.55, 0.085, 0.68, 0.53);

    /// A cubic animation curve that starts slowly and ends quickly. This curve is
    /// based on a cubic equation where `f(t) = t³`. The result is a safe sweet
    /// spot when choosing a curve for widgets animating off the viewport.
    ///
    /// Compared to [#EASE_IN_QUAD], this curve is slightly steeper.
    ///
    /// Derived from Robert Penner’s easing functions.
    ///
    /// @see <a href="https://flutter.github.io/assets-for-api-docs/assets/animation/curve_ease_in_cubic.mp4">curve_ease_in_cubic.mp4</a>
    public static final Interpolator EASE_IN_CUBIC = new Cubic(0.55, 0.055, 0.675, 0.19);

    /// A cubic animation curve that starts slowly and ends quickly. This curve is
    /// based on a quartic equation where `f(t) = t⁴`.
    ///
    /// Animations using this curve or steeper curves will benefit from a longer
    /// duration to avoid motion feeling unnatural.
    ///
    /// Compared to [#EASE_IN_CUBIC], this curve is slightly steeper.
    ///
    /// Derived from Robert Penner’s easing functions.
    ///
    /// @see <a href="https://flutter.github.io/assets-for-api-docs/assets/animation/curve_ease_in_quart.mp4">curve_ease_in_quart.mp4</a>
    public static final Interpolator EASE_IN_QUART = new Cubic(0.895, 0.03, 0.685, 0.22);

    /// A cubic animation curve that starts slowly and ends quickly. This curve is
    /// based on a quintic equation where `f(t) = t⁵`.
    ///
    /// Compared to [#EASE_IN_QUART], this curve is slightly steeper.
    ///
    /// Derived from Robert Penner’s easing functions.
    ///
    /// @see <a href="https://flutter.github.io/assets-for-api-docs/assets/animation/curve_ease_in_quint.mp4">curve_ease_in_quint.mp4</a>
    public static final Interpolator EASE_IN_QUINT = new Cubic(0.755, 0.05, 0.855, 0.06);

    /// A cubic animation curve that starts slowly and ends quickly. This curve is
    /// based on an exponential equation where `f(t) = 2¹⁰⁽ᵗ⁻¹⁾`.
    ///
    /// Using this curve can give your animations extra flare, but a longer
    /// duration may need to be used to compensate for the steepness of the curve.
    ///
    /// Compared to [#EASE_IN_QUINT], this curve is slightly steeper.
    ///
    /// Derived from Robert Penner’s easing functions.
    ///
    /// @see <a href="https://flutter.github.io/assets-for-api-docs/assets/animation/curve_ease_in_expo.mp4">curve_ease_in_expo.mp4</a>
    public static final Interpolator EASE_IN_EXPO = new Cubic(0.95, 0.05, 0.795, 0.035);

    /// A cubic animation curve that starts slowly and ends quickly. This curve is
    /// effectively the bottom-right quarter of a circle.
    ///
    /// Like [#EASE_IN_EXPO], this curve is fairly dramatic and will reduce
    /// the clarity of an animation if not given a longer duration.
    ///
    /// Derived from Robert Penner’s easing functions.
    ///
    /// @see <a href="https://flutter.github.io/assets-for-api-docs/assets/animation/curve_ease_in_circ.mp4">curve_ease_in_circ.mp4</a>
    public static final Interpolator EASE_IN_CIRC = new Cubic(0.6, 0.04, 0.98, 0.335);

    /// A cubic animation curve that starts slowly and ends quickly. This curve
    /// is similar to [#elasticIn] in that it overshoots its bounds before
    /// reaching its end. Instead of repeated swinging motions before ascending,
    /// though, this curve overshoots once, then continues to ascend.
    ///
    /// Derived from Robert Penner’s easing functions.
    ///
    /// @see <a href="https://flutter.github.io/assets-for-api-docs/assets/animation/curve_ease_in_back.mp4">curve_ease_in_back.mp4</a>
    public static final Interpolator EASE_IN_BACK = new Cubic(0.6, -0.28, 0.735, 0.045);

    /// A cubic animation curve that starts quickly and ends slowly.
    ///
    /// This is the same as the CSS easing function `ease-out`.
    ///
    /// @see <a href="https://flutter.github.io/assets-for-api-docs/assets/animation/curve_ease_out.mp4">curve_ease_out.mp4</a>
    public static final Interpolator EASE_OUT = new Cubic(0.0, 0.0, 0.58, 1.0);

    /// A cubic animation curve that starts linearly and ends slowly.
    ///
    /// A symmetric animation to [#EASE_IN_TO_LINEAR].
    ///
    /// @see <a href="https://flutter.github.io/assets-for-api-docs/assets/animation/curve_linear_to_ease_out.mp4">curve_linear_to_ease_out.mp4</a>
    public static final Interpolator LINEAR_TO_EASE_OUT = new Cubic(0.35, 0.91, 0.33, 0.97);

    /// A cubic animation curve that starts quickly and ends slowly. This is
    /// similar to [#EASE_OUT], but with sinusoidal easing for a slightly
    /// less abrupt beginning and end. Nonetheless, the result is quite gentle and
    /// is hard to distinguish from [#linear] at a glance.
    ///
    /// Derived from Robert Penner’s easing functions.
    ///
    /// @see <a href="https://flutter.github.io/assets-for-api-docs/assets/animation/curve_ease_out_sine.mp4">curve_ease_out_sine.mp4</a>
    public static final Interpolator EASE_OUT_SINE = new Cubic(0.39, 0.575, 0.565, 1.0);

    /// A cubic animation curve that starts quickly and ends slowly. This is
    /// effectively the same as [#decelerate], only simulated using a cubic
    /// bezier function.
    ///
    /// Compared to [#EASE_OUT_SINE], this curve is slightly steeper.
    ///
    /// Derived from Robert Penner’s easing functions.
    ///
    /// @see <a href="https://flutter.github.io/assets-for-api-docs/assets/animation/curve_ease_out_quad.mp4">curve_ease_out_quad.mp4</a>
    public static final Interpolator EASE_OUT_QUAD = new Cubic(0.25, 0.46, 0.45, 0.94);

    /// A cubic animation curve that starts quickly and ends slowly. This curve is
    /// a flipped version of [#EASE_IN_CUBIC].
    ///
    /// The result is a safe sweet spot when choosing a curve for animating a
    /// widget's position entering or already inside the viewport.
    ///
    /// Compared to [#EASE_OUT_QUAD], this curve is slightly steeper.
    ///
    /// Derived from Robert Penner’s easing functions.
    ///
    /// @see <a href="https://flutter.github.io/assets-for-api-docs/assets/animation/curve_ease_out_cubic.mp4">curve_ease_out_cubic.mp4</a>
    public static final Interpolator EASE_OUT_CUBIC = new Cubic(0.215, 0.61, 0.355, 1.0);

    /// A cubic animation curve that starts quickly and ends slowly. This curve is
    /// a flipped version of [#EASE_IN_QUART].
    ///
    /// Animations using this curve or steeper curves will benefit from a longer
    /// duration to avoid motion feeling unnatural.
    ///
    /// Compared to [#EASE_OUT_CUBIC], this curve is slightly steeper.
    ///
    /// Derived from Robert Penner’s easing functions.
    ///
    /// @see <a href="https://flutter.github.io/assets-for-api-docs/assets/animation/curve_ease_out_quart.mp4">curve_ease_out_quart.mp4</a>
    public static final Interpolator EASE_OUT_QUART = new Cubic(0.165, 0.84, 0.44, 1.0);

    /// A cubic animation curve that starts quickly and ends slowly. This curve is
    /// a flipped version of [#EASE_IN_QUINT].
    ///
    /// Compared to [#EASE_OUT_QUART], this curve is slightly steeper.
    ///
    /// Derived from Robert Penner’s easing functions.
    ///
    /// @see <a href="https://flutter.github.io/assets-for-api-docs/assets/animation/curve_ease_out_quint.mp4">curve_ease_out_quint.mp4</a>
    public static final Interpolator EASE_OUT_QUINT = new Cubic(0.23, 1.0, 0.32, 1.0);

    /// A cubic animation curve that starts quickly and ends slowly. This curve is
    /// a flipped version of [#EASE_IN_EXPO]. Using this curve can give your
    /// animations extra flare, but a longer duration may need to be used to
    /// compensate for the steepness of the curve.
    ///
    /// Derived from Robert Penner’s easing functions.
    ///
    /// @see <a href="https://flutter.github.io/assets-for-api-docs/assets/animation/curve_ease_out_expo.mp4">curve_ease_out_expo.mp4</a>
    public static final Interpolator EASE_OUT_EXPO = new Cubic(0.19, 1.0, 0.22, 1.0);

    /// A cubic animation curve that starts quickly and ends slowly. This curve is
    /// effectively the top-left quarter of a circle.
    ///
    /// Like [#EASE_OUT_EXPO], this curve is fairly dramatic and will reduce
    /// the clarity of an animation if not given a longer duration.
    ///
    /// Derived from Robert Penner’s easing functions.
    ///
    /// @see <a href="https://flutter.github.io/assets-for-api-docs/assets/animation/curve_ease_out_circ.mp4">curve_ease_out_circ.mp4</a>
    public static final Interpolator EASE_OUT_CIRC = new Cubic(0.075, 0.82, 0.165, 1.0);

    /// A cubic animation curve that starts quickly and ends slowly. This curve is
    /// similar to [#elasticOut] in that it overshoots its bounds before
    /// reaching its end. Instead of repeated swinging motions after ascending,
    /// though, this curve only overshoots once.
    ///
    /// Derived from Robert Penner’s easing functions.
    ///
    /// @see <a href="https://flutter.github.io/assets-for-api-docs/assets/animation/curve_ease_out_back.mp4">curve_ease_out_back.mp4</a>
    public static final Interpolator EASE_OUT_BACK = new Cubic(0.175, 0.885, 0.32, 1.275);

    /// A cubic animation curve that starts slowly, speeds up, and then ends
    /// slowly.
    ///
    /// This is the same as the CSS easing function `ease-in-out`.
    ///
    /// @see <a href="https://flutter.github.io/assets-for-api-docs/assets/animation/curve_ease_in_out.mp4">curve_ease_in_out.mp4</a>
    public static final Interpolator EASE_IN_OUT = new Cubic(0.42, 0.0, 0.58, 1.0);

    /// A cubic animation curve that starts slowly, speeds up, and then ends
    /// slowly. This is similar to [#EASE_IN_OUT], but with sinusoidal easing
    /// for a slightly less abrupt beginning and end.
    ///
    /// Derived from Robert Penner’s easing functions.
    ///
    /// @see <a href="https://flutter.github.io/assets-for-api-docs/assets/animation/curve_ease_in_out_sine.mp4">curve_ease_in_out_sine.mp4</a>
    public static final Interpolator EASE_IN_OUT_SINE = new Cubic(0.445, 0.05, 0.55, 0.95);

    /// A cubic animation curve that starts slowly, speeds up, and then ends
    /// slowly. This curve can be imagined as [#EASE_IN_QUAD] as the first
    /// half, and [#EASE_OUT_QUAD] as the second.
    ///
    /// Compared to [#EASE_IN_OUT_SINE], this curve is slightly steeper.
    ///
    /// Derived from Robert Penner’s easing functions.
    ///
    /// @see <a href="https://flutter.github.io/assets-for-api-docs/assets/animation/curve_ease_in_out_quad.mp4">curve_ease_in_out_quad.mp4</a>
    public static final Interpolator EASE_IN_OUT_QUAD = new Cubic(0.455, 0.03, 0.515, 0.955);

    /// A cubic animation curve that starts slowly, speeds up, and then ends
    /// slowly. This curve can be imagined as [#EASE_IN_CUBIC] as the first
    /// half, and [#EASE_OUT_CUBIC] as the second.
    ///
    /// The result is a safe sweet spot when choosing a curve for a widget whose
    /// initial and final positions are both within the viewport.
    ///
    /// Compared to [#EASE_IN_OUT_QUAD], this curve is slightly steeper.
    ///
    /// Derived from Robert Penner’s easing functions.
    ///
    /// @see <a href="https://flutter.github.io/assets-for-api-docs/assets/animation/curve_ease_in_out_cubic.mp4">curve_ease_in_out_cubic.mp4</a>
    public static final Interpolator EASE_IN_OUT_CUBIC = new Cubic(0.645, 0.045, 0.355, 1.0);

    /// A cubic animation curve that starts slowly, speeds up, and then ends
    /// slowly. This curve can be imagined as [#EASE_IN_QUART] as the first
    /// half, and [#EASE_OUT_QUART] as the second.
    ///
    /// Animations using this curve or steeper curves will benefit from a longer
    /// duration to avoid motion feeling unnatural.
    ///
    /// Compared to [#EASE_IN_OUT_CUBIC], this curve is slightly steeper.
    ///
    /// Derived from Robert Penner’s easing functions.
    ///
    /// @see <a href="https://flutter.github.io/assets-for-api-docs/assets/animation/curve_ease_in_out_quart.mp4">curve_ease_in_out_quart.mp4</a>
    public static final Interpolator EASE_IN_OUT_QUART = new Cubic(0.77, 0.0, 0.175, 1.0);

    /// A cubic animation curve that starts slowly, speeds up, and then ends
    /// slowly. This curve can be imagined as [#EASE_IN_QUINT] as the first
    /// half, and [#EASE_OUT_QUINT] as the second.
    ///
    /// Compared to [#EASE_IN_OUT_QUART], this curve is slightly steeper.
    ///
    /// Derived from Robert Penner’s easing functions.
    ///
    /// @see <a href="https://flutter.github.io/assets-for-api-docs/assets/animation/curve_ease_in_out_quint.mp4">curve_ease_in_out_quint.mp4</a>
    public static final Interpolator EASE_IN_OUT_QUINT = new Cubic(0.86, 0.0, 0.07, 1.0);

    /// A cubic animation curve that starts slowly, speeds up, and then ends
    /// slowly.
    ///
    /// Since this curve is arrived at with an exponential function, the midpoint
    /// is exceptionally steep. Extra consideration should be taken when designing
    /// an animation using this.
    ///
    /// Compared to [#EASE_IN_OUT_QUINT], this curve is slightly steeper.
    ///
    /// Derived from Robert Penner’s easing functions.
    ///
    /// @see <a href="https://flutter.github.io/assets-for-api-docs/assets/animation/curve_ease_in_out_expo.mp4">curve_ease_in_out_expo.mp4</a>
    public static final Interpolator EASE_IN_OUT_EXPO = new Cubic(1.0, 0.0, 0.0, 1.0);

    /// A cubic animation curve that starts slowly, speeds up, and then ends
    /// slowly. This curve can be imagined as [#EASE_IN_CIRC] as the first
    /// half, and [#EASE_OUT_CIRC] as the second.
    ///
    /// Like [#EASE_IN_OUT_EXPO], this curve is fairly dramatic and will reduce
    /// the clarity of an animation if not given a longer duration.
    ///
    /// Compared to [#EASE_IN_OUT_EXPO], this curve is slightly steeper.
    ///
    /// Derived from Robert Penner’s easing functions.
    ///
    /// @see <a href="https://flutter.github.io/assets-for-api-docs/assets/animation/curve_ease_in_out_circ.mp4">curve_ease_in_out_circ.mp4</a>
    public static final Interpolator EASE_IN_OUT_CIRC = new Cubic(0.785, 0.135, 0.15, 0.86);

    /// A cubic animation curve that starts slowly, speeds up shortly thereafter,
    /// and then ends slowly. This curve can be imagined as a steeper version of
    /// [#EASE_IN_OUT_CUBIC].
    ///
    /// The result is a more emphasized eased curve when choosing a curve for a
    /// widget whose initial and final positions are both within the viewport.
    ///
    /// Compared to [#EASE_IN_OUT_CUBIC], this curve is slightly steeper.
    ///
    /// @see <a href="https://flutter.github.io/assets-for-api-docs/assets/animation/curve_ease_in_out_cubic_emphasized.mp4">curve_ease_in_out_cubic_emphasized.mp4</a>
    public static final Interpolator EASE_IN_OUT_CUBIC_EMPHASIZED = new ThreePointCubic(
            new Offset(0.05, 0),
            new Offset(0.133333, 0.06),
            new Offset(0.166666, 0.4),
            new Offset(0.208333, 0.82),
            new Offset(0.25, 1)
    );

    /// A cubic animation curve that starts slowly, speeds up, and then ends
    /// slowly. This curve can be imagined as [#EASE_IN_BACK] as the first
    /// half, and [#EASE_OUT_BACK] as the second.
    ///
    /// Since two curves are used as a basis for this curve, the resulting
    /// animation will overshoot its bounds twice before reaching its end - first
    /// by exceeding its lower bound, then exceeding its upper bound and finally
    /// descending to its final position.
    ///
    /// Derived from Robert Penner’s easing functions.
    ///
    /// @see <a href="https://flutter.github.io/assets-for-api-docs/assets/animation/curve_ease_in_out_back.mp4">curve_ease_in_out_back.mp4</a>
    public static final Interpolator EASE_IN_OUT_BACK = new Cubic(0.68, -0.55, 0.265, 1.55);

    /// A curve that starts quickly and eases into its final position.
    ///
    /// Over the course of the animation, the object spends more time near its
    /// final destination. As a result, the user isn’t left waiting for the
    /// animation to finish, and the negative effects of motion are minimized.
    ///
    /// @see <a href="https://flutter.github.io/assets-for-api-docs/assets/animation/curve_fast_out_slow_in.mp4">curve_fast_out_slow_in.mp4</a>
    public static final Interpolator FAST_OUT_SLOW_IN = new Cubic(0.4, 0.0, 0.2, 1.0);

    /// A cubic animation curve that starts quickly, slows down, and then ends
    /// quickly.
    ///
    /// @see <a href="https://flutter.github.io/assets-for-api-docs/assets/animation/curve_slow_middle.mp4">curve_slow_middle.mp4</a>
    public static final Interpolator SLOW_MIDDLE = new Cubic(0.15, 0.85, 0.85, 0.15);

    private static double bounce(double t) {
        if (t < 1.0 / 2.75) {
            return 7.5625 * t * t;
        } else if (t < 2 / 2.75) {
            t -= 1.5 / 2.75;
            return 7.5625 * t * t + 0.75;
        } else if (t < 2.5 / 2.75) {
            t -= 2.25 / 2.75;
            return 7.5625 * t * t + 0.9375;
        }
        t -= 2.625 / 2.75;
        return 7.5625 * t * t + 0.984375;
    }

    /// An oscillating curve that grows in magnitude.
    ///
    /// @see <a href="https://flutter.github.io/assets-for-api-docs/assets/animation/curve_bounce_in.mp4">curve_bounce_in.mp4</a>

    public static final Interpolator BOUNCE_IN = new Interpolator() {
        @Override
        protected double curve(double t) {
            return 1.0 - bounce(1.0 - t);
        }
    };

    /// An oscillating curve that first grows and then shrink in magnitude.
    ///
    /// @see <a href="https://flutter.github.io/assets-for-api-docs/assets/animation/curve_bounce_out.mp4">curve_bounce_out.mp4</a>
    public static final Interpolator BOUNCE_OUT = new Interpolator() {
        @Override
        protected double curve(double t) {
            return bounce(t);
        }
    };

    /// An oscillating curve that first grows and then shrink in magnitude.
    ///
    /// @see <a href="https://flutter.github.io/assets-for-api-docs/assets/animation/curve_bounce_in_out.mp4">curve_bounce_in_out.mp4</a>
    public static final Interpolator BOUNCE_IN_OUT = new Interpolator() {
        @Override
        protected double curve(double t) {
            if (t < 0.5) {
                return (1.0 - bounce(1.0 - t * 2.0)) * 0.5;
            } else {
                return bounce(t * 2.0 - 1.0) * 0.5 + 0.5;
            }
        }
    };

    private static final double PERIOD = 0.4;

    /// An oscillating curve that grows in magnitude while overshooting its bounds.
    ///
    /// @see <a href="https://flutter.github.io/assets-for-api-docs/assets/animation/curve_elastic_in.mp4">curve_elastic_in.mp4</a>
    public static final Interpolator ELASTIC_IN = new Interpolator() {
        @Override
        protected double curve(double t) {
            final double s = PERIOD / 4.0;
            t = t - 1.0;
            return -Math.pow(2.0, 10.0 * t) * Math.sin((t - s) * (Math.PI * 2.0) / PERIOD);
        }
    };

    /// An oscillating curve that shrinks in magnitude while overshooting its bounds.
    ///
    /// @see <a href="https://flutter.github.io/assets-for-api-docs/assets/animation/curve_elastic_out.mp4">curve_elastic_out.mp4</a>
    public static Interpolator ELASTIC_OUT = new Interpolator() {
        @Override
        protected double curve(double t) {
            final double s = PERIOD / 4.0;
            return Math.pow(2.0, -10 * t) * Math.sin((t - s) * (Math.PI * 2.0) / PERIOD) + 1.0;
        }
    };

    /// An oscillating curve that grows and then shrinks in magnitude while overshooting its bounds.
    ///
    /// @see <a href="https://flutter.github.io/assets-for-api-docs/assets/animation/curve_elastic_in_out.mp4">curve_elastic_in_out.mp4</a>
    public static Interpolator ELASTIC_IN_OUT = new Interpolator() {
        @Override
        @SuppressWarnings("DuplicateExpressions")
        protected double curve(double t) {
            final double s = PERIOD / 4.0;
            t = 2.0 * t - 1.0;
            if (t < 0.0) {
                return -0.5 * Math.pow(2.0, 10.0 * t) * Math.sin((t - s) * (Math.PI * 2.0) / PERIOD);
            } else {
                return Math.pow(2.0, -10.0 * t) * Math.sin((t - s) * (Math.PI * 2.0) / PERIOD) * 0.5 + 1.0;
            }
        }
    };

    /// A cubic polynomial mapping of the unit interval.
    private static final class Cubic extends Interpolator {
        private static final double CUBIC_ERROR_BOUND = 0.001;

        /// The x coordinate of the first control point.
        ///
        /// The line through the point (0, 0) and the first control point is tangent
        /// to the curve at the point (0, 0).
        private final double a;

        /// The y coordinate of the first control point.
        ///
        /// The line through the point (0, 0) and the first control point is tangent
        /// to the curve at the point (0, 0).
        private final double b;

        /// The x coordinate of the second control point.
        ///
        /// The line through the point (1, 1) and the second control point is tangent
        /// to the curve at the point (1, 1).
        private final double c;

        /// The y coordinate of the second control point.
        ///
        /// The line through the point (1, 1) and the second control point is tangent
        /// to the curve at the point (1, 1).
        private final double d;

        private Cubic(double a, double b, double c, double d) {
            this.a = a;
            this.b = b;
            this.c = c;
            this.d = d;
        }

        double _evaluateCubic(double a, double b, double m) {
            return 3 * a * (1 - m) * (1 - m) * m + 3 * b * (1 - m) * m * m + m * m * m;
        }

        @Override
        protected double curve(double t) {
            double start = 0.0;
            double end = 1.0;
            while (true) {
                final double midpoint = (start + end) / 2;
                final double estimate = _evaluateCubic(a, c, midpoint);
                if (Math.abs(t - estimate) < CUBIC_ERROR_BOUND) {
                    return _evaluateCubic(b, d, midpoint);
                }
                if (estimate < t) {
                    start = midpoint;
                } else {
                    end = midpoint;
                }
            }
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Cubic cubic
                    && this.a == cubic.a
                    && this.b == cubic.b
                    && this.c == cubic.c
                    && this.d == cubic.d;
        }

        @Override
        public int hashCode() {
            return Objects.hash(a, b, c, d);
        }

        @Override
        public String toString() {
            return "Cubic[a=%s, b=%s, c=%s, d=%s]".formatted(a, b, c, d);
        }
    }

    private record Offset(double dx, double dy) {
    }

    private static final class ThreePointCubic extends Interpolator {

        /// The coordinates of the first control point of the first curve.
        ///
        /// The line through the point (0, 0) and this control point is tangent to the
        /// curve at the point (0, 0).
        private final Offset a1;

        /// The coordinates of the second control point of the first curve.
        ///
        /// The line through the [#midpoint] and this control point is tangent to the
        /// curve approaching the [#midpoint].
        private final Offset b1;

        /// The coordinates of the middle shared point.
        ///
        /// The curve will go through this point. If the control points surrounding
        /// this middle point ([#b1], and [#a2]) are not colinear with this point, then
        /// the curve's derivative will have a discontinuity (a cusp) at this point.
        private final Offset midpoint;

        /// The coordinates of the first control point of the second curve.
        ///
        /// The line through the [#midpoint] and this control point is tangent to the
        /// curve approaching the [#midpoint].
        private final Offset a2;

        /// The coordinates of the second control point of the second curve.
        ///
        /// The line through the point (1, 1) and this control point is tangent to the
        /// curve at (1, 1).
        private final Offset b2;

        /// Creates two cubic curves that share a common control point.
        ///
        /// Rather than creating a new instance, consider using one of the common
        /// three-point cubic curves in [Interpolator].
        ///
        /// The arguments correspond to the control points for the two curves,
        /// including the [#midpoint], but do not include the two implied end points at
        /// (0,0) and (1,1), which are fixed.
        private ThreePointCubic(Offset a1, Offset b1, Offset midpoint, Offset a2, Offset b2) {
            this.a1 = a1;
            this.b1 = b1;
            this.midpoint = midpoint;
            this.a2 = a2;
            this.b2 = b2;
        }

        @Override
        protected double curve(double t) {
            final boolean firstCurve = t < midpoint.dx;
            final double scaleX = firstCurve ? midpoint.dx : 1.0 - midpoint.dx;
            final double scaleY = firstCurve ? midpoint.dy : 1.0 - midpoint.dy;
            final double scaledT = (t - (firstCurve ? 0.0 : midpoint.dx)) / scaleX;
            if (firstCurve) {
                return new Cubic(
                        a1.dx / scaleX,
                        a1.dy / scaleY,
                        b1.dx / scaleX,
                        b1.dy / scaleY
                ).curve(scaledT) *
                        scaleY;
            } else {
                return new Cubic(
                        (a2.dx - midpoint.dx) / scaleX,
                        (a2.dy - midpoint.dy) / scaleY,
                        (b2.dx - midpoint.dx) / scaleX,
                        (b2.dy - midpoint.dy) / scaleY
                ).curve(scaledT) *
                        scaleY +
                        midpoint.dy;
            }
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ThreePointCubic that
                    && a1.equals(that.a1)
                    && b1.equals(that.b1)
                    && midpoint.equals(that.midpoint)
                    && a2.equals(that.a2)
                    && b2.equals(that.b2);
        }

        @Override
        public int hashCode() {
            return Objects.hash(a1, b1, midpoint, a2, b2);
        }

        @Override
        public String toString() {
            return "ThreePointCubic[a1=%s, b1=%s, midpoint=%s, a2=%s, b2=%s]".formatted(a1, b1, midpoint, a2, b2);
        }
    }

    //endregion Curves

    // region Durations

    /// The short1 duration (50ms) in the Material specification.
    public static final Duration SHORT1 = Duration.millis(50);

    /// The short2 duration (100ms) in the Material specification.
    public static final Duration SHORT2 = Duration.millis(100);

    /// The short3 duration (150ms) in the Material specification.
    public static final Duration SHORT3 = Duration.millis(150);

    /// The short4 duration (200ms) in the Material specification.
    public static final Duration SHORT4 = Duration.millis(200);

    /// The medium1 duration (250ms) in the Material specification.
    public static final Duration MEDIUM1 = Duration.millis(250);

    /// The medium2 duration (300ms) in the Material specification.
    public static final Duration MEDIUM2 = Duration.millis(300);

    /// The medium3 duration (350ms) in the Material specification.
    public static final Duration MEDIUM3 = Duration.millis(350);

    /// The medium4 duration (400ms) in the Material specification.
    public static final Duration MEDIUM4 = Duration.millis(400);

    /// The long1 duration (450ms) in the Material specification.
    public static final Duration LONG1 = Duration.millis(450);

    /// The long2 duration (500ms) in the Material specification.
    public static final Duration LONG2 = Duration.millis(500);

    /// The long3 duration (550ms) in the Material specification.
    public static final Duration LONG3 = Duration.millis(550);

    /// The long4 duration (600ms) in the Material specification.
    public static final Duration LONG4 = Duration.millis(600);

    /// The extralong1 duration (700ms) in the Material specification.
    public static final Duration EXTRA_LONG1 = Duration.millis(700);

    /// The extralong2 duration (800ms) in the Material specification.
    public static final Duration EXTRA_LONG2 = Duration.millis(800);

    /// The extralong3 duration (900ms) in the Material specification.
    public static final Duration EXTRA_LONG3 = Duration.millis(900);

    /// The extralong4 duration (1000ms) in the Material specification.
    public static final Duration EXTRA_LONG4 = Duration.millis(1000);

    // endregion Durations

    private Motion() {
    }
}
