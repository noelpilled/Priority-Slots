package com.priorityslots.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public final class PrioritySlotsIcon
{
	private static final int SIZE = 16;

	private PrioritySlotsIcon()
	{
	}

	public static BufferedImage create()
	{
		BufferedImage image = new BufferedImage(
			SIZE,
			SIZE,
			BufferedImage.TYPE_INT_ARGB
		);

		Graphics2D graphics = image.createGraphics();

		try
		{
			graphics.setRenderingHint(
				RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON
			);

			graphics.setStroke(
				new BasicStroke(
					1.5f,
					BasicStroke.CAP_ROUND,
					BasicStroke.JOIN_ROUND
				)
			);

			graphics.setColor(new Color(255, 152, 31));
			graphics.drawRoundRect(1, 1, 13, 13, 3, 3);

			graphics.fillRoundRect(3, 3, 8, 2, 2, 2);
			graphics.fillRoundRect(3, 7, 6, 2, 2, 2);
			graphics.fillRoundRect(3, 11, 4, 2, 2, 2);
		}
		finally
		{
			graphics.dispose();
		}

		return image;
	}
}
