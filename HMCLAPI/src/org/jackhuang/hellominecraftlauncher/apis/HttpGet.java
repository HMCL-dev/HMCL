/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.apis;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;

/**
 * 网络操作类
 * @author hyh
 */
public class HttpGet {

    /**
     * Sends an HTTP GET request to a url
     *
     * @param endpoint - The URL of the server. (Example: "
     * http://www.yahoo.com/search")
     * @param requestParameters - all the request parameters (Example:
     * "param1=val1&param2=val2"). Note: This method will add the question mark
     * (?) to the request - DO NOT add it yourself
     * @return - The response from the end point
     */
    public static String sendGetRequest(String endpoint,
            String requestParameters) {
        String result = null;
        if (endpoint.startsWith("http://")) {
            // Send a GET request to the servlet
            try {
                // Construct data
                StringBuilder data = new StringBuilder();
                // Send data
                String urlStr = endpoint;
                if (requestParameters != null && requestParameters.length() > 0) {
                    urlStr += "?" + requestParameters;
                }
                URL url = new URL(urlStr);
                URLConnection conn = url.openConnection();
                conn.setRequestProperty(result, urlStr);

                // Get the response
                InputStreamReader r = new InputStreamReader(conn.getInputStream());
                StringBuffer sb;
                BufferedReader rd = new BufferedReader(r);
                sb = new StringBuffer();
                String line;
                while ((line = rd.readLine()) != null)
                    sb.append(line);
                result = sb.toString();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    public static String post(String url, Map<String, String> params) {
        URL u = null;
        HttpURLConnection con = null;
        //构建请求参数
        StringBuilder sb = new StringBuilder();
        if (params != null) {
            for (Map.Entry<String, String> e : params.entrySet()) {
                sb.append(e.getKey());
                sb.append("=");
                sb.append(e.getValue());
                sb.append("&");
            }
            sb = new StringBuilder(sb.substring(0, sb.length() - 1));
        }
        System.out.println("send_url:" + url);
        System.out.println("send_data:" + sb.toString());
        //尝试发送请求
        try {
            u = new URL(url);
            con = (HttpURLConnection) u.openConnection();
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setDoInput(true);
            con.setUseCaches(false);
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            OutputStreamWriter osw = new OutputStreamWriter(con.getOutputStream(), "UTF-8");
            osw.write(sb.toString());
            osw.flush();
            osw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        //读取返回内容
        StringBuilder buffer = new StringBuilder();
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(con
                    .getInputStream(), "UTF-8"));
            String temp;
            while ((temp = br.readLine()) != null) {
                buffer.append(temp);
                buffer.append("\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        con.disconnect();

        return buffer.toString();
    }
}
