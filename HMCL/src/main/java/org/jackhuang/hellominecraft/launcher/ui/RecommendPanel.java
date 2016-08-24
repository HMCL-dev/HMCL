/*
 * Copyright (C) 2016 evilwk <evilwk@gmail.com>
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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.jackhuang.hellominecraft.launcher.ui;

import java.awt.Graphics;
import java.awt.Image;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.SwingWorker;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import org.jackhuang.hellominecraft.launcher.Main;
import org.jackhuang.hellominecraft.launcher.setting.Settings;
import org.jackhuang.hellominecraft.util.C;
import org.jackhuang.hellominecraft.util.NetUtils;
import org.jackhuang.hellominecraft.util.StrUtils;
import org.jackhuang.hellominecraft.util.ui.SwingUtils;

/**
 *
 * @author evilwk <evilwk@gmail.com>
 */
public class RecommendPanel extends JPanel {

	private static final int SWITCH_INTERVAL = 10;
	
	private static final int SPACE = 10;
	private static final int TOP_POSITION = 2;
	
	private JButton closeButton;
	
	private Image currImage;
	private String imageKey = null;
	private boolean ignoreSwitch = false;
	private List<RecommendInfo> recommends;

	public ScheduledExecutorService scheduledexec = Executors.newScheduledThreadPool(1);

	public RecommendPanel() {
		initComponents();
		
		recommends = new ArrayList<RecommendInfo>();
		new LoadImages().execute();
		setCursor(new Cursor(Cursor.HAND_CURSOR));

		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				MouseClicked(e);
			}
		});
	}
	
	private void initComponents() {
		this.setLayout(null);
		
		closeButton = new JButton(Main.getIcon("re_close.png"));
		closeButton.setRolloverIcon(Main.getIcon("re_close_enter.png"));
		closeButton.setBorder(BorderFactory.createEmptyBorder());
		closeButton.setContentAreaFilled(false);
		closeButton.addActionListener((e) -> {
			synchronized(RecommendPanel.class) {
				if (StrUtils.isNotBlank(imageKey)) {
					Settings.getInstance().getIgnoreRecommend().add(imageKey);
					Settings.save();
					
					ignoreSwitch = true;
					showNext();
				}
			}
		});
		closeButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
		closeButton.setFocusable(false);
		closeButton.setBounds(0, 0, 12, 12);
		closeButton.setVisible(false);
		this.add(closeButton);
	}
	
	private void MouseClicked(MouseEvent evt) {                                     
		if (imageKey == null) {
			return;
		}
		RecommendInfo info = recommends.get(getCurrentImageIndex());
		if (info.link != null && !info.link.equals("")) {
			SwingUtils.openLink(info.link);
		}
    }                                    

	public void showImages() {
		if (recommends.isEmpty()) {
			return;
		}

		new Thread(new Runnable() {
			@Override
			public void run() {
				for (RecommendInfo info : recommends) {
					if (!ignoreShowUrl(info.url)) {
						try {
							File tempFile = File.createTempFile("hmcl", "png");
							String tempPath = tempFile.getCanonicalPath();
							if (NetUtils.download(info.url, tempPath)) {
								info.image = ImageIO.read(tempFile);
							}
						} catch (Throwable t) { }
					}
				}

				if (getImagesSize() == 0 || showIfOnly()) {
					return;
				}
				scheduledexec.scheduleAtFixedRate(new Runnable() {
					@Override
					public void run() {
						SwingUtilities.invokeLater(new Runnable() {
							@Override
							public void run() {
								synchronized(RecommendPanel.class) {
									if (ignoreSwitch) {
										ignoreSwitch = false;
									} else {
										showNext();
									}
								}
							}
						});
					}
				}, 0, SWITCH_INTERVAL, TimeUnit.SECONDS);
			}
		}).start();
	}

	public int getImagesSize() {
		int imageCount = 0;
		for (RecommendInfo recommend : recommends) {
			if (recommend.image != null) {
				imageCount++;
			}
		}
		return imageCount;
	}

	public boolean showIfOnly() {
		if (getImagesSize() != 1) {
			return false;
		}
		for (RecommendInfo recommend : recommends) {
			if (recommend.image != null) {
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						RecommendPanel.this.setImage(recommend.url, recommend.image);
					}
				});
			}
		}
		return true;
	}
	
	private void showNext() {
		if (getCanShowImageCount() == 0) {
			setVisible(false);
		} else {
			int showIndex = getNextImageIndex();
			RecommendInfo info = recommends.get(showIndex);
			setImage(info.url, info.image);	
		}
	}
	
	private boolean ignoreShowUrl(String url) {
		return Settings.getInstance().getIgnoreRecommend().contains(url);
	}

	public int getCanShowImageCount() {
		int imageCount = 0;
		for (RecommendInfo recommend : recommends) {
			if (recommend.image != null && !ignoreShowUrl(recommend.url)) {
				imageCount++;
			}
		}
		return imageCount;
	}
	
	public int getNextImageIndex(int showIndex) {
		if (showIndex >= recommends.size()) {
			showIndex = 0;
		}
		RecommendInfo info = recommends.get(showIndex);
		if (info.image == null || ignoreShowUrl(info.url)) {
			showIndex = getNextImageIndex(++showIndex);	
		}
		return showIndex;
	}

	public int getNextImageIndex() {
		int showIndex = getCurrentImageIndex();
		if (++showIndex >= recommends.size()) {
			showIndex = 0;
		}
		showIndex = getNextImageIndex(showIndex);
		return showIndex >= recommends.size() ? 0 : showIndex;
	}
	
	public int getCurrentImageIndex() {
		int currIndex = 0;
		for (int i = 0; i < recommends.size(); i++) {
			RecommendInfo info = recommends.get(i);
			if (imageKey != null && info.url.equals(imageKey)) {
				currIndex = i;
				break;
			}
		}
		return currIndex;
	}
	
	public void setImage(String key, Image image) {
		this.imageKey = key;
		this.currImage = image;
		
		int btnWidth = closeButton.getWidth();
		
		setSize(image.getWidth(this) + SPACE + btnWidth, image.getHeight(this));
		closeButton.setLocation(getWidth() - btnWidth, TOP_POSITION);
		closeButton.setVisible(true);
		
		SwingUtilities.updateComponentTreeUI(this.getRootPane());
	}

	@Override
	public void paintComponent(Graphics g) {
		if (currImage != null) {
			g.drawImage(currImage, 0, 0, currImage.getWidth(this), currImage.getHeight(this), this);
		}
		super.paintComponent(g);
	}

	static class RecommendInfo {
		String url;
		String link;
		Image image;
	}

	class LoadImages extends SwingWorker<List<Map<String, String>>, Void> {

		private static final String RECOMMEND_URL = "http://client.api.mcgogogo.com:81/recommend.php";

		@Override
		protected List<Map<String, String>> doInBackground() throws Exception {
			List<Map<String, String>> infos = null;
			do {
				String content = NetUtils.get(RECOMMEND_URL);
				if (content == null || content.equals("")) {
					break;
				}

				Map<String, Object> data = new Gson().fromJson(content,
						new TypeToken<Map<String, Object>>() {}.getType());
				if (data == null) {
					break;
				}

				infos = (List<Map<String, String>>) data.get("data");
			} while (false);
			return infos;
		}

		@Override
		protected void done() {
			try {
				List<Map<String, String>> infos = this.get();
				if (infos == null) {
					return;
				}
				for (Map<String, String> info : infos) {
					RecommendInfo recommend = new RecommendInfo();
					recommend.url = info.get("url");
					recommend.link = info.get("link");
					recommend.image = null;
					recommends.add(recommend);
				}
				RecommendPanel.this.showImages();
			} catch (InterruptedException | ExecutionException ex) {
			}
		}
	}

}
