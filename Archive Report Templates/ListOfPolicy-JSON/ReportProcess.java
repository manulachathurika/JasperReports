package com.aviva.report;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRExporterParameter;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRXmlDataSource;
import net.sf.jasperreports.engine.data.JsonDataSource;
import net.sf.jasperreports.engine.export.JRXlsExporter;
import net.sf.jasperreports.engine.export.JRXlsExporterParameter;
import net.sf.jasperreports.engine.util.JRLoader;
import net.sf.jasperreports.engine.util.SimpleFileResolver;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.log4j.Logger;
import com.aviva.report.util.Constant;

public class ReportProcess implements Processor {
	
	private static final Logger LOGGER = Logger.getLogger(ReportProcess.class);

	public void process(Exchange original) throws Exception {
		
		LOGGER.info("Report Processor Initiated");
		
		Map<String,Object> result = new HashMap<String, Object>();
		Map<String,String> inputObj = new HashMap<String, String>();
		
		CamelContext ctx = original.getContext();
		PropertiesComponent co = ctx.getComponent(Constant.CAMEL_PROPERTIES, PropertiesComponent.class);
		Properties p = co.getPropertiesResolver().resolveProperties(ctx, true, Constant.PROPERTIES);
		
		try{
			
			inputObj = original.getIn().getBody(Map.class);
			result.put(Constant.REPORT_ID, inputObj.get(Constant.REPORT_ID));
			
			//If report ID null do not continue;
			String reportId = inputObj.get(Constant.REPORT_ID);
			String reportTemplate = inputObj.get(Constant.REPORT_TEMPLATE);
			String fileExt = inputObj.get(Constant.REPORT_TYPE);
			String reportName = inputObj.get(Constant.REPORT_NAME);
			
			String xmlDataSource = original.getIn().getHeader("xmlDataFile",String.class);
			LOGGER.info("xmlDataSource : " + xmlDataSource);
			
			String dataSourceFileName = xmlDataSource;
			
			String sourceFileExt = inputObj.get(Constant.SOURCE_FILE_TYPE);
			
			LOGGER.info("********** Source file type : " + sourceFileExt);
			
			LOGGER.info("Source Path " + inputObj.get(Constant.SOURCE_PATH));
			LOGGER.info("destination Path " + inputObj.get(Constant.DESTINATION_PATH));
			String destinationPath = null;
			
			if (!"null".equals(inputObj.get(Constant.DESTINATION_PATH))) {
				destinationPath = inputObj.get(Constant.DESTINATION_PATH);	
			} else {
				destinationPath = p.getProperty(Constant.DEFAULT_REPORT_DEST_PATH);
			}
			
			LOGGER.info("Destination path :" + destinationPath);
			String reportPath = p.getProperty(Constant.REPORT_SRC_TEMPLATE);
			
			if (!"null".equals(inputObj.get(Constant.SOURCE_PATH))) {
				xmlDataSource = inputObj.get(Constant.SOURCE_PATH).concat(xmlDataSource);
			} else {
				xmlDataSource = p.getProperty(Constant.REPORT_SRC_XML).concat(xmlDataSource);
			}
			
			String reportsDirPath = p.getProperty(Constant.REPORT_SRC_TEMPLATE);
			File reportsDir = new File(reportsDirPath);
			if (!reportsDir.exists()) {
			    throw new FileNotFoundException(String.valueOf(reportsDir));
			}
			
			Map<String, Object> reportParams = new HashMap<String,Object>();
			
			reportParams.put(JRParameter.REPORT_FILE_RESOLVER, new SimpleFileResolver(reportsDir));
			reportParams.put(Constant.REPORT_PARAM_PATH, reportPath);
			reportParams.put("template_folder_path", p.getProperty(Constant.REPORT_SRC_TEMPLATE));
			//reportParams.put("SUBREPORT_DIR", p.getProperty(Constant.REPORT_SRC_TEMPLATE));
			//reportParams.put("template_folder_path_tmp", p.getProperty(Constant.REPORT_SRC_TEMPLATE));
			
			LOGGER.info("TEMPLATE_DIR : " + p.getProperty(Constant.REPORT_SRC_TEMPLATE));
			LOGGER.info("XML Datasource : " + xmlDataSource);
			LOGGER.info("Report Type about to genrate : " + fileExt);
			
			//xmlDataSource = xmlDataSource.replace("/", "\\");//For windows
			
			InputStream inputStream = null;
			
			if ("xls".equalsIgnoreCase(fileExt)) {
				inputStream = xlsReportGen(xmlDataSource.trim(), reportTemplate, reportPath, reportParams);
			} else if ("pdf".equalsIgnoreCase(fileExt)) {
				inputStream = pdfReportGen(xmlDataSource, reportTemplate, reportPath, reportParams, sourceFileExt);
			}
			
			LOGGER.info("Report generation complete");
			
			original.getIn().setHeader(Constant.STATUS, Constant.SUCCESS);
			
			DateFormat dateFormat = new SimpleDateFormat(Constant.DATE_FORMAT);
			Date date = new Date();
			
			String reportFileName = null;
			
			if (sourceFileExt.equalsIgnoreCase(Constant.JSON_EXT)) {
				String[] fName = dataSourceFileName.split("_");
				reportFileName = fName[0] + "_" + "Form" + "." + fileExt;
				LOGGER.info("********** 1");
			} else if (!"null".equals(reportName)) {
				reportFileName = reportName + "-" + dateFormat.format(date) + "." + fileExt;
				LOGGER.info("********** 2");
			} else {
				reportFileName = reportId + "-" + dateFormat.format(date) + "." + fileExt;
				LOGGER.info("********** 3");
			}
			
			LOGGER.info("Genrated Report file Name : " + reportFileName);
			
			original.getIn().setHeader("fileName", reportFileName);
			original.getIn().setHeader(Constant.SOURCE_PATH, inputObj.get(Constant.SOURCE_PATH));
			original.getIn().setHeader(Constant.DESTINATION_PATH, destinationPath);
			original.getIn().setBody(inputStream);
			
		} catch (Exception e) {
			LOGGER.error("Report Processor Error");
			original.getIn().setHeader(Constant.STATUS, Constant.ERROR);
			result.put(Constant.STATUS, Constant.ERROR);
			result.put(Constant.DESCRIPTION, e.getMessage());
			original.getIn().setBody(result);
			original.getIn().setHeader("CamelFileName", "");
		}
		
		LOGGER.info("Report Processor Completed");
	}
	
	public InputStream pdfReportGen(String fileName, String reportTemplate, String reportPath, Map<String,Object> reportParams, String sourceFileExt) throws Exception {

		JRDataSource datasource = null;
			
		if (sourceFileExt.equalsIgnoreCase(Constant.JSON_EXT)) {
			datasource = new JsonDataSource(new File(fileName));
		} else {
			datasource = new JRXmlDataSource(new File(fileName));
		}
		
		LOGGER.info("Binding datasource to template");
		LOGGER.info("fileName "+fileName);
		LOGGER.info("Report Template path "+reportPath);
		LOGGER.info("Report template "+reportTemplate);
		
		JasperReport jasperReport = (JasperReport)JRLoader.loadObjectFromFile(reportPath + reportTemplate);
		JasperPrint _print = JasperFillManager.fillReport(jasperReport,  reportParams, datasource);
		return new ByteArrayInputStream(JasperExportManager.exportReportToPdf(_print));
	}
	
	public InputStream xlsReportGen(String xml, String reportTemplate, String reportPath, Map<String,Object> reportParams) throws Exception {

		ByteArrayOutputStream _out = new ByteArrayOutputStream();
		//JRDataSource datasource = new JRXmlDataSource(new File(xml));
		LOGGER.info("********** File path is : " + xml);
		//JRDataSource datasource = new JsonDataSource(new File(xml));
		JRDataSource datasource = new JsonDataSource(new File(xml), "policyReportDTOList");
		JasperReport jasperReport = (JasperReport)JRLoader.loadObjectFromFile(reportPath + reportTemplate);
		JasperPrint _print = JasperFillManager.fillReport(jasperReport,  reportParams, datasource);
		
		JRXlsExporter exporter = new JRXlsExporter();
		exporter.setParameter(JRExporterParameter.JASPER_PRINT, _print);
		exporter.setParameter(JRExporterParameter.OUTPUT_STREAM, _out);
		
		exporter.setParameter(JRXlsExporterParameter.IS_REMOVE_EMPTY_SPACE_BETWEEN_ROWS,Boolean.TRUE);
        exporter.exportReport();

		return new ByteArrayInputStream(_out.toByteArray());
	}
}
