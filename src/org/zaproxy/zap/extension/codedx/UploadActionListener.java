/*
 * Copyright (C) 2016 Code Dx, Inc. - http://www.codedx.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.zaproxy.zap.extension.codedx;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.parosproxy.paros.Constant;
import org.parosproxy.paros.view.View;

public class UploadActionListener implements ActionListener{

	private static final Logger logger = Logger.getLogger(UploadActionListener.class);
	
	private CodeDxExtension extension;
	private UploadPropertiesDialog prop;
	
	public UploadActionListener(CodeDxExtension extension) {
		this.extension = extension;
		this.prop = new UploadPropertiesDialog(extension);
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		prop.openProperties(this);
	}
	
	public void generateAndUploadReport(){
		String error = null;
		try {
			
			final StringBuilder report = new StringBuilder();
			final File reportFile = generateReport(report);
			
			if(!"".equals(report.toString().trim()) && report.toString().split("\n").length > 2){
				Thread uploadThread = new Thread(){
					@Override
					public void run(){
						String msg = null;
						String err = null;

						try{
							HttpResponse response = sendData(reportFile);
							StatusLine responseLine = null;
							int responseCode = -1;
							if(response != null){
								responseLine = response.getStatusLine();
								responseCode = responseLine.getStatusCode();
							}
							if(responseCode == 202){
								msg = Constant.messages.getString("codedx.message.success");
							} else if(responseCode == 400) {
								err = Constant.messages.getString("codedx.error.unexpected") + "\n"
										+ Constant.messages.getString("codedx.error.http.400");
							} else if(responseCode == 403){
								err = Constant.messages.getString("codedx.error.unsent") + " "
										+ Constant.messages.getString("codedx.error.http.403");
							} else if(responseCode == 404){
								err = Constant.messages.getString("codedx.error.unsent") + " "
										+ Constant.messages.getString("codedx.error.http.404");
							} else if(responseCode == 415) {
								err = Constant.messages.getString("codedx.error.unexpected") + "\n"
										+ Constant.messages.getString("codedx.error.http.415");
							} else if(response != null) {
								err = Constant.messages.getString("codedx.error.unexpected")
										+ Constant.messages.getString("codedx.error.http.other") + " " + responseLine;
							}
						} catch (IOException ex1){
							err = Constant.messages.getString("codedx.error.unexpected");
							logger.error("Unexpected error while uploading report: ", ex1);
						}
						if(msg != null)
							View.getSingleton().showMessageDialog(msg);
						if(err != null)
							View.getSingleton().showMessageDialog(err);
						reportFile.delete();
					}
				};
				uploadThread.start();
			} else {
				error = Constant.messages.getString("codedx.error.empty");
			}
		} catch (Exception ex2) {
			error = Constant.messages.getString("codedx.error.failed");
			logger.error("Unexpected error while generating report: ", ex2);
		}
		if(error != null)
			View.getSingleton().showWarningDialog(error);
	}
	
	private HttpResponse sendData(File report) throws IOException{
		CloseableHttpClient client = extension.getHttpClient();
		if(client == null)
			return null;
		HttpPost post = new HttpPost(CodeDxProperties.getServerUrl() + "/api/projects/"
				+ prop.getProject().getValue() + "/analysis");
		post.setHeader("API-Key", CodeDxProperties.getApiKey());
		
		MultipartEntityBuilder builder = MultipartEntityBuilder.create();
		builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
		builder.addPart("file", new FileBody(report));		
		
		HttpEntity entity = builder.build();
		post.setEntity(entity);

		HttpResponse response = client.execute(post);
		HttpEntity resEntity = response.getEntity();
		
		if (resEntity != null) {
			EntityUtils.consume(resEntity);
		}
		client.close();
		
		return response;
	}
	
	private File generateReport(StringBuilder report) throws Exception{
		ReportLastScanHttp saver = new ReportLastScanHttp();
		saver.generate(report, extension.getModel());
		
		String OS = System.getProperty("os.name").toUpperCase(Locale.getDefault());
		Path env;
		if (OS.contains("WIN")){
			env = Paths.get(System.getenv("APPDATA"),"Code Dx","ZAP");
		}
		else if (OS.contains("MAC")){
			env = Paths.get(System.getProperty("user.home"),"Library","Application Support","Code Dx","ZAP");
		}
		else if (OS.contains("NUX")){
			env = Paths.get(System.getProperty("user.home"),".codedx","ZAP");
		}
		else{
			env = Paths.get(System.getProperty("user.dir"),"codedx","ZAP");
		}
		File reportFile = env.toFile();
		if(!reportFile.exists())
			reportFile.mkdirs();
		
		reportFile = new File(reportFile, "ZAP.xml");
		if(reportFile.exists())
			reportFile.delete();
		
		Files.write(reportFile.toPath(), report.toString().getBytes(), StandardOpenOption.CREATE_NEW);
		return reportFile;
	}
}
