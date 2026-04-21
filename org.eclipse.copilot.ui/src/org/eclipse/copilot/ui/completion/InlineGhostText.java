/*******************************************************************************
 * Copyright (c) 2025 GitHub, Inc. and others
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *******************************************************************************/

package org.eclipse.copilot.ui.completion;

import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.GlyphMetrics;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;

/**
 * A ghost text placed in the line (not end of line). For single line ghost text, we draw it by ourselves. Because the
 * code mining API will put the cursor at the end of the line, which is not what we want.
 */
public class InlineGhostText extends GhostText {

  /**
   * Creates a new InlineGhostText.
   */
  public InlineGhostText(String text, int modelOffset) {
    super(text, modelOffset, GhostTextType.IN_LINE);
  }

  /**
   * see {@link org.eclipse.jface.text.source.inlined.InlinedAnnotationDrawingStrategy#drawAsLeftOf1stCharacter}.
   */
  @Override
  public void draw(StyledText styledText, int widgetOffset, GC gc) {
    String hostCharacter = styledText.getText(widgetOffset, widgetOffset);
    // Compute the location of the ghost text
    Rectangle bounds = styledText.getTextBounds(widgetOffset, widgetOffset);

    int x = bounds.x;
    int y = bounds.y;
    // When line text has line header annotation, there is a space on the top, adjust the y by using char height
    y += Math.max(0, bounds.height - styledText.getLineHeight());

    gc.drawString(text, x, y, true);

    StyleRange style = styledText.getStyleRangeAtOffset(widgetOffset);
    int redrawnCharacterWidth = hostCharacter.charAt(0) != '\t' ? gc.stringExtent(hostCharacter).x
        : styledText.getTabs() * gc.stringExtent(" ").x;
    int textWidth = gc.stringExtent(text).x;
    StyleRange newStyle = updateStyle(widgetOffset, text, style, gc.getFontMetrics(), redrawnCharacterWidth, textWidth);
    if (newStyle != null) {
      styledText.setStyleRange(newStyle);
      return;
    }

    // The inline annotation replaces one character by taking a place width
    // GlyphMetrics
    // Here we need to redraw this first character because GlyphMetrics clip this
    // character.
    gc.setForeground(styledText.getForeground());
    gc.setBackground(styledText.getBackground());
    gc.setFont(styledText.getFont());

    // Get size of the character where GlyphMetrics width is added
    Point charBounds = gc.stringExtent(hostCharacter);
    int charWidth = charBounds.x;
    int redrawnHostCharX = x + bounds.width - charWidth;
    int redrawnHostCharY = y;
    if (style != null) {
      if (style.background != null) {
        gc.setBackground(style.background);
        gc.fillRectangle(redrawnHostCharX, y, charWidth + 1, bounds.height);
      }

      if (style.foreground != null) {
        gc.setForeground(style.foreground);
      }

      if (style.font != null) {
        gc.setFont(style.font);
      }
    }

    if (styledText.getSelection().x <= widgetOffset && styledText.getSelection().y > widgetOffset) {
      gc.setForeground(styledText.getSelectionForeground());
      gc.setBackground(styledText.getSelectionBackground());
    }
    gc.drawString(hostCharacter, redrawnHostCharX, redrawnHostCharY, true);
  }

  private static StyleRange updateStyle(int widgetOffset, String text, StyleRange style, FontMetrics fontMetrics,
      int redrawnCharacterWidth, int textWidth) {
    int fullWidth = textWidth + redrawnCharacterWidth;
    StyleRange newStyle;
    if (style == null) {
      newStyle = new StyleRange();
      newStyle.start = widgetOffset;
      newStyle.length = 1;
    } else {
      // Clone the existing style to preserve all attributes (colors, borders, etc.)
      // that may have been set by other plugins like SonarQube
      newStyle = (StyleRange) style.clone();

      // CRITICAL: Constrain the cloned style to only the single character at widgetOffset.
      //
      // Why length=1?
      // - getStyleRangeAtOffset() can return a StyleRange spanning multiple characters
      //   (e.g., SonarQube may mark an entire method name with borders/colors)
      // - We only need to attach GlyphMetrics to ONE character (the host character that
      //   the inline ghost text visually replaces in the editor)
      // - Setting length=1 ensures we don't accidentally apply GlyphMetrics to the entire
      //   multi-character range, which would corrupt other plugins' decorations
      //
      // Why not 0 or -1?
      // - length=0 would be invalid (StyleRange requires length >= 1)
      // - length=-1 has no defined behavior in SWT StyleRange API
      // - length=1 is the minimum valid range that targets exactly the character we need
      //
      // This prevents the SonarQube red rectangle bug where our GlyphMetrics were being
      // applied to entire warning ranges instead of just the insertion point.
      newStyle.start = widgetOffset;
      newStyle.length = 1;
    }

    GlyphMetrics metrics = newStyle.metrics;
    if (text != null) {
      if (metrics == null) {
        metrics = new GlyphMetrics(fontMetrics.getAscent(), fontMetrics.getDescent(), fullWidth);
      } else {
        if (metrics.width == fullWidth) {
          return null;
        }

        metrics = new GlyphMetrics(fontMetrics.getAscent(), fontMetrics.getDescent(), fullWidth);
      }
    } else {
      metrics = null;
    }

    newStyle.metrics = metrics;
    return newStyle;
  }

}
