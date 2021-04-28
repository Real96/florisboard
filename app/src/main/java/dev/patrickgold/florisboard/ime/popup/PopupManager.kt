/*
 * Copyright (C) 2020 Patrick Goldinger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.popup

import android.content.res.Configuration
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat.getDrawable
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.media.emoji.EmojiKeyboardView
import dev.patrickgold.florisboard.ime.text.key.*
import dev.patrickgold.florisboard.ime.text.keyboard.EmojiKey
import dev.patrickgold.florisboard.ime.text.keyboard.Key
import dev.patrickgold.florisboard.ime.text.keyboard.TextKey
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyboardView

/**
 * Manages the creation and dismissal of key popups as well as the checks if the pointer moved
 * out of the popup bound (only for extended popups).
 *
 * @property keyboardView Reference to the keyboard view to which this manager class belongs to.
 */
class PopupManager<V : View>(
    private val keyboardView: V,
    private val popupLayerView: PopupLayerView?
) {
    private var anchorLeft: Boolean = false
    private var anchorRight: Boolean = false
    private var anchorOffset: Int = 0
    private val exceptionsForKeyCodes = listOf(
        KeyCode.ENTER,
        KeyCode.LANGUAGE_SWITCH,
        KeyCode.SWITCH_TO_TEXT_CONTEXT,
        KeyCode.SWITCH_TO_MEDIA_CONTEXT,
        KeyCode.SWITCH_TO_CLIPBOARD_CONTEXT
    )
    private var keyPopupWidth: Int
    private var keyPopupHeight: Int
    private var keyPopupTextSize: Float = keyboardView.resources.getDimension(R.dimen.key_popup_textSize)
    private var keyPopupDiffX: Int = 0
    private val popupView: PopupView
    private val popupViewExt: PopupExtendedView
    private var row0count: Int = 0
    private var row1count: Int = 0

    /** Is true if the preview popup is visible to the user, else false */
    val isShowingPopup: Boolean
        get() = popupView.isShowing
    /** Is true if the extended popup is visible to the user, else false */
    val isShowingExtendedPopup: Boolean
        get() = popupViewExt.isShowing

    companion object {
        const val POPUP_EXTENSION_PATH_REL: String = "ime/text/characters/extended_popups"
    }

    init {
        keyPopupWidth = keyboardView.resources.getDimension(R.dimen.key_width).toInt()
        keyPopupHeight = keyboardView.resources.getDimension(R.dimen.key_height).toInt()
        popupView = PopupView(keyboardView.context)
        popupViewExt = PopupExtendedView(keyboardView.context)
        popupLayerView?.addView(popupView)
        popupLayerView?.addView(popupViewExt)
    }

    /**
     * Helper function to create a element for the extended popup and preconfigure it.
     *
     * @param key Reference to the key currently controlling the popup.
     * @param adjustedIndex The index of the key in the key data popup array.
     * @return A preconfigured extended popup element.
     */
    private fun createElement(
        key: Key,
        adjustedIndex: Int
    ): PopupExtendedView.Element {
        return when (key) {
            is TextKey -> {
                when (key.computedPopups[adjustedIndex].code) {
                    KeyCode.SETTINGS -> {
                        getDrawable(keyboardView.context, R.drawable.ic_settings)?.let {
                            PopupExtendedView.Element.Icon(it, adjustedIndex)
                        } ?: PopupExtendedView.Element.Undefined
                    }
                    KeyCode.SWITCH_TO_TEXT_CONTEXT -> {
                        PopupExtendedView.Element.Label(
                            keyboardView.resources.getString(R.string.key__view_characters), adjustedIndex
                        )
                    }
                    KeyCode.SWITCH_TO_MEDIA_CONTEXT -> {
                        getDrawable(keyboardView.context, R.drawable.ic_sentiment_satisfied)?.let {
                            PopupExtendedView.Element.Icon(it, adjustedIndex)
                        } ?: PopupExtendedView.Element.Undefined
                    }
                    KeyCode.SWITCH_TO_CLIPBOARD_CONTEXT -> {
                        getDrawable(keyboardView.context, R.drawable.ic_assignment)?.let {
                            PopupExtendedView.Element.Icon(it, adjustedIndex)
                        } ?: PopupExtendedView.Element.Undefined
                    }
                    KeyCode.URI_COMPONENT_TLD -> {
                        PopupExtendedView.Element.Tld(
                            key.computedPopups[adjustedIndex].asString(isForDisplay = true), adjustedIndex
                        )
                    }
                    KeyCode.TOGGLE_ONE_HANDED_MODE_LEFT,
                    KeyCode.TOGGLE_ONE_HANDED_MODE_RIGHT -> {
                        getDrawable(keyboardView.context, R.drawable.ic_smartphone)?.let {
                            PopupExtendedView.Element.Icon(it, adjustedIndex)
                        } ?: PopupExtendedView.Element.Undefined
                    }
                    else -> {
                        PopupExtendedView.Element.Label(
                            key.computedPopups[adjustedIndex].asString(isForDisplay = true), adjustedIndex
                        )
                    }
                }
            }
            is EmojiKey -> {
                PopupExtendedView.Element.Label(
                    key.computedPopups[adjustedIndex].asString(isForDisplay = true), adjustedIndex
                )
            }
            else -> {
                PopupExtendedView.Element.Undefined
            }
        }
    }

    /**
     * Calculates all attributes required by both the normal and the extended popup, regardless of
     * the passed [key]'s code.
     */
    private fun calc(key: Key) {
        if (keyboardView is TextKeyboardView) {
            when (keyboardView.resources.configuration.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> {
                    if (keyboardView.isSmartbarKeyboardView) {
                        keyPopupWidth = (key.visibleBounds.width() * 1.0f).toInt()
                        keyPopupHeight = (keyboardView.desiredKey.visibleBounds.height() * 3.0f * 1.2f).toInt()
                    } else {
                        keyPopupWidth = (keyboardView.desiredKey.visibleBounds.width() * 1.0f).toInt()
                        keyPopupHeight = (keyboardView.desiredKey.visibleBounds.height() * 3.0f).toInt()
                    }
                }
                else -> {
                    if (keyboardView.isSmartbarKeyboardView) {
                        keyPopupWidth = (key.visibleBounds.width() * 1.1f).toInt()
                        keyPopupHeight = (keyboardView.desiredKey.visibleBounds.height() * 2.5f * 1.2f).toInt()
                    } else {
                        keyPopupWidth = (keyboardView.desiredKey.visibleBounds.width() * 1.1f).toInt()
                        keyPopupHeight = (keyboardView.desiredKey.visibleBounds.height() * 2.5f).toInt()
                    }
                }
            }
        } else if (keyboardView is EmojiKeyboardView) {
            keyPopupWidth = key.visibleBounds.width()
            keyPopupHeight = (key.visibleBounds.height() * 2.5f).toInt()
        }
        keyPopupDiffX = (key.visibleBounds.width() - keyPopupWidth) / 2
    }

    /**
     * Shows a preview popup for the passed [key]. Ignores show requests for keys which
     * key code is equal to or less than [KeyCode.SPACE].
     *
     * @param key Reference to the key currently controlling the popup.
     */
    fun show(key: Key, keyHintMode: KeyHintMode) {
        if (key is TextKey && key.computedData.code <= KeyCode.SPACE) {
            return
        }

        calc(key)

        popupView.properties.apply {
            width = keyPopupWidth
            height = keyPopupHeight
            xOffset = keyPopupDiffX
            yOffset = -keyPopupHeight
            innerLabelFactor = 0.4f
            label = when (key) {
                is TextKey -> key.computedData.asString(isForDisplay = true)
                is EmojiKey -> key.computedData.asString(isForDisplay = true)
                else -> ""
            }
            labelTextSize = keyPopupTextSize
            shouldIndicateExtendedPopups = when (key) {
                is TextKey -> key.computedPopups.size(keyHintMode) > 0
                is EmojiKey -> key.computedPopups.isNotEmpty()
                else -> false
            }
        }
        popupView.show(keyboardView, key)
    }

    /**
     * Extends the currently showing key preview popup if there are popup keys defined in the
     * key data of the passed [key]. Ignores extend requests for key views which key code
     * is equal to or less than [KeyCode.SPACE]. An exception is made for the codes defined in
     * [exceptionsForKeyCodes], as they most likely have special keys bound to them.
     *
     * Layout of the extended key popup: (n = key.computedPopups.size)
     *   when n <= 5: single line, row0 only
     *     _ _ _ _ _
     *     K K K K K
     *   when n > 5 && n % 2 == 1: multi line, row0 has 1 more key than row1, empty space position
     *     is depending on the current anchor
     *     anchorLeft           anchorRight
     *     K K ... K _         _ K ... K K
     *     K K ... K K         K K ... K K
     *   when n > 5 && n % 2 == 0: multi line, both same length
     *     K K ... K K
     *     K K ... K K
     *
     * @param key Reference to the key currently controlling the popup.
     */
    fun extend(key: Key, keyHintMode: KeyHintMode) {
        if (key is TextKey && key.computedData.code <= KeyCode.SPACE
            && !exceptionsForKeyCodes.contains(key.computedData.code)) {
            return
        }

        if (!isShowingPopup) {
            calc(key)
        }

        // Anchor left if keyView is in left half of keyboardView, else anchor right
        anchorLeft = key.visibleBounds.left < keyboardView.measuredWidth / 2
        anchorRight = !anchorLeft

        // Determine key counts for each row
        val n = when (key) {
            is TextKey -> key.computedPopups.size(keyHintMode)
            //is EmojiKeyView -> key.data.popup.size
            else -> 0
        }
        when {
            n <= 0 -> return
            n <= 5 -> {
                row1count = 0
                row0count = n
            }
            n > 5 && n % 2 == 1 -> {
                row1count = (n - 1) / 2
                row0count = (n + 1) / 2
            }
            else -> {
                row1count = n / 2
                row0count = n / 2
            }
        }

        // Calculate anchor offset (always positive int, direction depends on anchorLeft and
        // anchorRight state)
        anchorOffset = when {
            row0count <= 1 -> 0
            else -> {
                var offset = when {
                    row0count % 2 == 1 -> (row0count - 1) / 2
                    row0count % 2 == 0 -> (row0count / 2) - 1
                    else -> 0
                }
                val availableSpace = when {
                    anchorLeft -> key.visibleBounds.left + keyPopupDiffX
                    anchorRight -> keyboardView.measuredWidth -
                            (key.visibleBounds.left + keyPopupDiffX + keyPopupWidth)
                    else -> 0
                }
                while (offset > 0) {
                    if (availableSpace >= offset * keyPopupWidth) {
                        break
                    } else {
                        offset -= 1
                    }
                }
                offset
            }
        }

        // Build UI
        popupViewExt.properties.elements.clear()
        val initUiIndex = when {
            anchorLeft -> anchorOffset + row1count
            anchorRight -> row0count - 1 - anchorOffset + row1count
            else -> 0
        }
        val popupIndices: IntArray
        val uiIndices = IntRange(0, (n - 1).coerceAtLeast(0))
        if (key is TextKey) {
            popupIndices = IntArray(n) { 0 }
            when (keyHintMode) {
                KeyHintMode.ENABLED_ACCENT_PRIORITY -> when {
                    key.computedPopups.main != null -> {
                        popupIndices[initUiIndex] = PopupSet.MAIN_INDEX
                        if (key.computedPopups.hint != null) when {
                            initUiIndex + 1 < n -> popupIndices[initUiIndex + 1] = PopupSet.HINT_INDEX
                            initUiIndex - 1 >= 0 -> popupIndices[initUiIndex - 1] = PopupSet.HINT_INDEX
                        }
                    }
                    key.computedPopups.hint != null -> when {
                        initUiIndex + 1 < n -> popupIndices[initUiIndex + 1] = PopupSet.HINT_INDEX
                        initUiIndex - 1 >= 0 -> popupIndices[initUiIndex - 1] = PopupSet.HINT_INDEX
                        else -> popupIndices[initUiIndex] = PopupSet.HINT_INDEX
                    }
                }
                KeyHintMode.ENABLED_HINT_PRIORITY -> when {
                    key.computedPopups.hint != null -> {
                        popupIndices[initUiIndex] = PopupSet.HINT_INDEX
                        if (key.computedPopups.main != null) when {
                            initUiIndex + 1 < n -> popupIndices[initUiIndex + 1] = PopupSet.MAIN_INDEX
                            initUiIndex - 1 >= 0 -> popupIndices[initUiIndex - 1] = PopupSet.MAIN_INDEX
                        }
                    }
                    key.computedPopups.main != null -> popupIndices[initUiIndex] = PopupSet.MAIN_INDEX
                }
                KeyHintMode.ENABLED_SMART_PRIORITY -> when {
                    key.computedPopups.main != null -> {
                        popupIndices[initUiIndex] = PopupSet.MAIN_INDEX
                        if (key.computedPopups.hint != null) when {
                            initUiIndex + 1 < n -> popupIndices[initUiIndex + 1] = PopupSet.HINT_INDEX
                            initUiIndex - 1 >= 0 -> popupIndices[initUiIndex - 1] = PopupSet.HINT_INDEX
                        }
                    }
                    key.computedPopups.hint != null -> popupIndices[initUiIndex] = PopupSet.HINT_INDEX
                }
                KeyHintMode.DISABLED -> when {
                    key.computedPopups.main != null -> popupIndices[initUiIndex] = PopupSet.MAIN_INDEX
                }
            }
            var offset = 0
            for (uiIndex in uiIndices) {
                if (popupIndices[uiIndex] < 0) {
                    offset++
                } else {
                    popupIndices[uiIndex] = uiIndex - offset
                }
            }
        } else {
            popupIndices = IntArray(n) { it }
        }
        if (row1count > 0) {
            popupViewExt.properties.elements.add(mutableListOf())
        }
        popupViewExt.properties.elements.add(mutableListOf())
        for (uiIndex in uiIndices) {
            val rowIndex = if (uiIndex < row1count && row1count > 0) { 1 } else { 0 }
            popupViewExt.properties.elements[rowIndex].add(
                createElement(key, popupIndices[uiIndex])
            )
        }

        // Calculate layout params
        val extWidth = row0count * keyPopupWidth
        val extHeight = when {
            row1count > 0 -> keyPopupHeight * 0.4f * 2.0f
            else -> keyPopupHeight * 0.4f
        }.toInt()
        val x = ((key.visibleBounds.width() - keyPopupWidth) / 2) + when {
            anchorLeft -> -anchorOffset * keyPopupWidth
            anchorRight -> -extWidth + keyPopupWidth + anchorOffset * keyPopupWidth
            else -> 0
        }
        val y = -keyPopupHeight - when {
            row1count > 0 -> (keyPopupHeight * 0.4f).toInt()
            else -> 0
        }

        popupViewExt.properties.apply {
            width = extWidth
            height = extHeight
            xOffset = x
            yOffset = y
            gravity = if (anchorLeft) { Gravity.START } else { Gravity.END }
            labelTextSize = keyPopupTextSize
            activeElementIndex = initUiIndex
        }
        popupViewExt.show(keyboardView, key)

        popupView.properties.shouldIndicateExtendedPopups = false
        popupView.invalidate()
    }

    /**
     * Updates the current selected key in extended popup according to the passed [event].
     * This function does nothing if the extended popup is not showing and will return false.
     *
     * @param key Reference to the key currently controlling the popup.
     * @param event The [MotionEvent] passed from the keyboard view's onTouch event.
     * @return True if the pointer movement is within the elements bounds, false otherwise.
     */
    fun propagateMotionEvent(key: Key, event: MotionEvent, pointerIndex: Int): Boolean {
        if (!isShowingExtendedPopup) {
            return false
        }

        val x = event.getX(pointerIndex) - key.visibleBounds.left
        val y = event.getY(pointerIndex) - key.visibleBounds.top
        val kX: Float = x / keyPopupWidth.toFloat()

        // Check if out of boundary on y-axis
        if (y < -keyPopupHeight || y > 0.9f * keyPopupHeight) {
            return false
        }

        popupViewExt.properties.activeElementIndex = when {
            anchorLeft -> when {
                // check if out of boundary on x-axis
                x < keyPopupDiffX - (anchorOffset + 1) * keyPopupWidth ||
                x > (keyPopupDiffX + (row0count + 1 - anchorOffset) * keyPopupWidth) -> {
                    return false
                }
                // row 1
                y < 0 && row1count > 0 -> when {
                    kX >= row1count - anchorOffset -> row1count - 1
                    kX < -anchorOffset -> 0
                    kX < 0 -> kX.toInt() - 1 + anchorOffset
                    else -> kX.toInt() + anchorOffset
                }
                // row 0
                else -> when {
                    kX >= row0count - anchorOffset -> row1count + row0count - 1
                    kX < -anchorOffset -> row1count
                    kX < 0 -> row1count + kX.toInt() - 1 + anchorOffset
                    else -> row1count + kX.toInt() + anchorOffset
                }
            }
            anchorRight -> when {
                // check if out of boundary on x-axis
                x > key.visibleBounds.width() - keyPopupDiffX + (anchorOffset + 1) * keyPopupWidth ||
                x < (key.visibleBounds.width() - keyPopupDiffX - (row0count + 1 - anchorOffset) * keyPopupWidth) -> {
                    return false
                }
                // row 1
                y < 0 && row1count > 0 -> when {
                    kX >= anchorOffset -> row1count - 1
                    kX < -(row1count - 1 - anchorOffset) -> 0
                    kX < 0 -> row1count - 2 + kX.toInt() - anchorOffset
                    else -> row1count - 1 + kX.toInt() - anchorOffset
                }
                // row 0
                else -> when {
                    kX >= anchorOffset -> row1count + row0count - 1
                    kX < -(row0count - 1 - anchorOffset) -> row1count
                    kX < 0 -> row1count + row0count - 2 + kX.toInt() - anchorOffset
                    else -> row1count + row0count - 1 + kX.toInt() - anchorOffset
                }
            }
            else -> -1
        }
        popupViewExt.invalidate()

        return true
    }

    /**
     * Gets the [KeyData] of the currently active key. May be either the key of the popup preview
     * or one of the keys in extended popup, if shown. Returns null if [key] is not a subclass of [TextKey].
     *
     * @param key Reference to the key currently controlling the popup.
     * @return The [KeyData] object of the currently active key or null.
     */
    fun getActiveKeyData(key: Key): TextKeyData? {
        return if (key is TextKey) {
            val element = popupViewExt.properties.getElementOrNull()
            if (element != null) {
                key.computedPopups.getOrNull(element.adjustedIndex) ?: key.computedData
            } else {
                key.computedData
            }
        } else {
            null
        }
    }

    /**
     * Gets the [EmojiKeyData] of the currently active key. May be either the key of the popup
     * preview or one of the keys in extended popup, if shown. Returns null if [key] is noz a subclass of [EmojiKey].
     *
     * @param key Reference to the key currently controlling the popup.
     * @return The [EmojiKeyData] object of the currently active key or null.
     */
    fun getActiveEmojiKeyData(key: Key): EmojiKeyData? {
        return if (key is EmojiKey) {
            val element = popupViewExt.properties.getElementOrNull()
            if (element != null) {
                key.computedPopups.getOrNull(element.adjustedIndex) ?: key.computedData
            } else {
                key.computedData
            }
        } else {
            null
        }
    }

    /**
     * Hides the key preview popup as well as the extended popup.
     */
    fun hide() {
        popupView.hide()
        popupViewExt.hide()
        popupViewExt.properties.activeElementIndex = -1
    }

    /**
     * Dismisses all currently shown popups. Should be called by the keyboard view when it
     * is closing.
     */
    fun dismissAllPopups() {
        popupView.hide()
        popupLayerView?.removeView(popupView)
        popupViewExt.hide()
        popupViewExt.properties.activeElementIndex = -1
        popupLayerView?.removeView(popupViewExt)
    }
}
