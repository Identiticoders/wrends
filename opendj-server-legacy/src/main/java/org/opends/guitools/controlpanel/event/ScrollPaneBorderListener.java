/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */

package org.opends.guitools.controlpanel.event;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

import javax.swing.BorderFactory;
import javax.swing.JScrollPane;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;

/**
 * This is a listener that is basically used to update dynamically the border
 * of a scroll bar.  This is used when we do not want to display the borders of
 * the scrollpane if no scrollbars are visible.  So the code basically adds
 * a component listener to the scroll pane and depending on whether the scroll
 * bars are displayed or not some border to the scroll pane is added (or not).
 */
public class ScrollPaneBorderListener extends ComponentAdapter
{
  private JScrollPane scroll;
  private Border emptyBorder = new EmptyBorder(0, 0, 0, 0);
  private Border etchedBorder = BorderFactory.createMatteBorder(0, 0, 1, 0,
      ColorAndFontConstants.defaultBorderColor);

  /** Private constructor. */
  private ScrollPaneBorderListener()
  {
  }

  /**
   * Returns a scroll pane border listener that will apply a border only on the
   * bottom of the scroll.
   * @param scroll the scroll pane to update.
   * @return a scroll pane border listener that will apply a border only on the
   * bottom of the scroll.
   */
  public static ScrollPaneBorderListener createBottomBorderListener(
      JScrollPane scroll)
  {
    ScrollPaneBorderListener listener = new ScrollPaneBorderListener();
    listener.scroll = scroll;
    scroll.getHorizontalScrollBar().addComponentListener(listener);
    scroll.getVerticalScrollBar().addComponentListener(listener);
    return listener;
  }

  /**
   * Returns a scroll pane border listener that will apply a border on the
   * bottom and on the top of the scroll.
   * @param scroll the scroll pane to update.
   * @return a scroll pane border listener that will apply a border on the
   * bottom and on the top of the scroll.
   */
  public static ScrollPaneBorderListener createBottomAndTopBorderListener(
      JScrollPane scroll)
  {
    ScrollPaneBorderListener listener = createBottomBorderListener(scroll);
    listener.etchedBorder = BorderFactory.createMatteBorder(1, 0, 1, 0,
        ColorAndFontConstants.defaultBorderColor);
    return listener;
  }

  /**
   * Returns a scroll pane border listener that will apply a full border to the
   * scroll.
   * @param scroll the scroll pane to update.
   * @return a scroll pane border listener that will apply a full border to the
   * scroll.
   */
  public static ScrollPaneBorderListener createFullBorderListener(
      JScrollPane scroll)
  {
    ScrollPaneBorderListener listener = createBottomBorderListener(scroll);
    listener.etchedBorder = BorderFactory.createMatteBorder(1, 1, 1, 1,
        ColorAndFontConstants.defaultBorderColor);
    return listener;
  }

  @Override
  public void componentShown(ComponentEvent ev)
  {
    updateBorder();
  }

  @Override
  public void componentHidden(ComponentEvent ev)
  {
    updateBorder();
  }

  /** Updates the border depending on whether the scroll bars are visible or not. */
  public void updateBorder()
  {
    boolean displayBorder = scroll.getVerticalScrollBar().isVisible() ||
    scroll.getHorizontalScrollBar().isVisible();

    if (displayBorder)
    {
      scroll.setBorder(etchedBorder);
    }
    else
    {
      scroll.setBorder(emptyBorder);
    }
  }
}
