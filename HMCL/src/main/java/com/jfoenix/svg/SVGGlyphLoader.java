/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.jfoenix.svg;

import javafx.beans.binding.Bindings;
import javafx.scene.paint.Color;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Set;

/**
 * will load icomoon svg font file, it will create a map of the
 * available svg glyphs. the user can retrieve the svg glyph using its name.
 *
 * @author Shadi Shaheen
 * @version 1.0
 * @since 2016-03-09
 */
public class SVGGlyphLoader {

    private static final HashMap<String, SVGGlyphBuilder> glyphsMap = new HashMap<>();

    public static SVGGlyph getGlyph(String glyphName) {
        return glyphsMap.get(glyphName).build();
    }

    /**
     * will retrieve icons from the glyphs map for a certain glyphName
     *
     * @param glyphName the glyph name
     * @return SVGGlyph node
     */
    public static SVGGlyph getIcoMoonGlyph(String glyphName) throws Exception{
        SVGGlyphBuilder builder = glyphsMap.get(glyphName);
        if(builder == null) throw new Exception("Glyph '" + glyphName + "' not found!");
        SVGGlyph glyph = builder.build();
        // we need to apply transformation to correct the icon since
        // its being inverted after importing from icomoon
        glyph.getTransforms().add(new Scale(1, -1));
        Translate height = new Translate();
        height.yProperty().bind(Bindings.createDoubleBinding(() -> -glyph.getHeight(), glyph.heightProperty()));
        glyph.getTransforms().add(height);
        return glyph;
    }

    /**
     * @return a set of all loaded svg IDs (names)
     */
    public static Set<String> getAllGlyphsIDs() {
        return glyphsMap.keySet();
    }

    /**
     * will load SVG icons from icomoon font file (e.g font.svg)
     *
     * @param url of the svg font file
     */
    public static void loadGlyphsFont(URL url) throws IOException {
        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            docBuilder.setEntityResolver((publicId, systemId) -> {
                // disable dtd entites at runtime
                return new InputSource(new StringReader(""));
            });

            File svgFontFile = new File(url.toURI());
            Document doc = docBuilder.parse(svgFontFile);
            doc.getDocumentElement().normalize();

            NodeList glyphsList = doc.getElementsByTagName("glyph");
            for (int i = 0; i < glyphsList.getLength(); i++) {
                Node glyph = glyphsList.item(i);
                Node glyphName = glyph.getAttributes().getNamedItem("glyph-name");
                if (glyphName == null) {
                    continue;
                }

                String glyphId = glyphName.getNodeValue();
                SVGGlyphBuilder glyphPane = new SVGGlyphBuilder(i,
                    glyphId,
                    glyph.getAttributes()
                        .getNamedItem("d")
                        .getNodeValue());
                glyphsMap.put(svgFontFile.getName() + "." + glyphId, glyphPane);
            }
        } catch (ParserConfigurationException | SAXException | URISyntaxException e) {
            e.printStackTrace();
        }
    }

    /**
     * will load SVG icons from input stream
     *
     * @param stream    input stream of svg font file
     * @param keyPrefix will be used as a prefix when storing SVG icons in the map
     */
    public static void loadGlyphsFont(InputStream stream, String keyPrefix) throws IOException {
        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            docBuilder.setEntityResolver((publicId, systemId) -> {
                // disable dtd entites at runtime
                return new InputSource(new StringReader(""));
            });

            Document doc = docBuilder.parse(stream);
            doc.getDocumentElement().normalize();

            NodeList glyphsList = doc.getElementsByTagName("glyph");
            for (int i = 0; i < glyphsList.getLength(); i++) {
                Node glyph = glyphsList.item(i);
                Node glyphName = glyph.getAttributes().getNamedItem("glyph-name");
                if (glyphName == null) {
                    continue;
                }

                String glyphId = glyphName.getNodeValue();
                SVGGlyphBuilder glyphPane = new SVGGlyphBuilder(i,
                    glyphId,
                    glyph.getAttributes()
                        .getNamedItem("d")
                        .getNodeValue());
                glyphsMap.put(keyPrefix + "." + glyphId, glyphPane);
            }
            stream.close();
        } catch (ParserConfigurationException | SAXException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * load a single svg icon from a file
     *
     * @param url of the svg icon
     * @return SVGGLyph node
     * @throws IOException
     */
    public static SVGGlyph loadGlyph(URL url) throws IOException {
        String urlString = url.toString();
        String filename = urlString.substring(urlString.lastIndexOf('/') + 1);

        int startPos = 0;
        int endPos = 0;
        while (endPos < filename.length() && filename.charAt(endPos) != '-') {
            endPos++;
        }
        int id = Integer.parseInt(filename.substring(startPos, endPos));
        startPos = endPos + 1;

        while (endPos < filename.length() && filename.charAt(endPos) != '.') {
            endPos++;
        }
        String name = filename.substring(startPos, endPos);

        return new SVGGlyph(id, name, extractSvgPath(getStringFromInputStream(url.openStream())), Color.BLACK);
    }

    /**
     * clear all loaded svg icons
     */
    public static void clear() {
        glyphsMap.clear();
    }

    private static String extractSvgPath(String svgString) {
        return svgString.replaceFirst(".*d=\"", "").replaceFirst("\".*", "");
    }

    private static String getStringFromInputStream(InputStream is) {
        BufferedReader br = null;
        StringBuilder sb = new StringBuilder();

        String line;
        try {
            br = new BufferedReader(new InputStreamReader(is));
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return sb.toString();
    }

    private static final class SVGGlyphBuilder {
        private final int glyphId;
        private final String name;
        private final String svgPathContent;

        SVGGlyphBuilder(int glyphId, String name, String svgPathContent) {
            this.glyphId = glyphId;
            this.name = name;
            this.svgPathContent = svgPathContent;
        }

        SVGGlyph build() {
            return new SVGGlyph(glyphId, name, svgPathContent, Color.BLACK);
        }
    }
}

