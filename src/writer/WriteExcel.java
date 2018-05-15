package writer;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.TreeMap;

import core.DTNHost;
import jxl.Cell;
import jxl.CellView;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.format.UnderlineStyle;
import jxl.read.biff.BiffException;
import jxl.write.Formula;
import jxl.write.Label;
import jxl.write.Number;
import jxl.write.WritableCellFormat;
import jxl.write.WritableFont;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;
import jxl.write.biff.RowsExceededException;
import streaming.NodeProperties;


public class WriteExcel {

    private WritableCellFormat timesBoldUnderline;
    private WritableCellFormat times;
    private String inputFile;
    
    private String[] header = {"HostName", "AverageWaitTime", "TimeFirstRequested","TimeFirstChunkReceived", "TimeStartedPlaying", "TimeLastPlayed",
    		"ACK", "LastChunkReceived", "#ofTimesInterrupted", "#ofSkippedChunks", "TotalChunksReceived", "#ofDuplicateChunksReceived", "#ofTimesRequested", "#ofDuplicateRequest", 
                               "TotalIndexFragmentSent", "TotalTransFragmentSent", "TotalChunksSent", "#ofFragmentsCreated (IndexLevel)", "#ofTimesAdjusted",
                               "SeedNo."};
    TreeMap<DTNHost, NodeProperties> nodeRecord = new TreeMap<DTNHost, NodeProperties>();
    
    Workbook workbook;
    WritableWorkbook wb;
    WritableSheet excelSheet;
    
    public void setOutputFile(String inputFile) {
    	this.inputFile = inputFile;
    }

    public void init() throws IOException, WriteException, BiffException{
    	File file = new File(inputFile);
        WorkbookSettings wbSettings = new WorkbookSettings(); 
        wbSettings.setLocale(new Locale("en", "EN"));
        
        if (file.exists()){
			workbook = Workbook.getWorkbook(file, wbSettings);
			wb = Workbook.createWorkbook(file, workbook);
			System.out.println(" CREATED WORKBOOK");
			excelSheet = wb.getSheet(0); 
			initLabel();
		} 
        else{
        	System.out.println("@ exception1");
        	wb = Workbook.createWorkbook(file, wbSettings);
        	wb.createSheet("Report", 0);
        	  excelSheet = wb.getSheet(0); 
        	initLabel();
        	createLabel(excelSheet);   	
            System.out.println("@ exception");
        }
     
//      wb.getSheets()
    }
    
    public void initLabel() throws WriteException{
        // Lets create a times font
        WritableFont times10pt = new WritableFont(WritableFont.TIMES, 10);
        // Define the cell format
        times = new WritableCellFormat(times10pt);
        // Lets automatically wrap the cells
        times.setWrap(true);

        // create create a bold font with unterlines
        WritableFont times10ptBoldUnderline = new WritableFont(
                WritableFont.TIMES, 10, WritableFont.BOLD, false,
                UnderlineStyle.SINGLE);
        timesBoldUnderline = new WritableCellFormat(times10ptBoldUnderline);
        // Lets automatically wrap the cells
        timesBoldUnderline.setWrap(true);

        CellView cv = new CellView();
        cv.setFormat(times);
        cv.setFormat(timesBoldUnderline);
        cv.setAutosize(true);

    }
    
    public void write(TreeMap<DTNHost, NodeProperties> nodeRecord, int seed){
    	this.nodeRecord = nodeRecord;    	
    
    	int row = excelSheet.getRows(); //
		try {
	        createContent(excelSheet, row, seed);
		} catch (WriteException e) {
			e.printStackTrace();
		}

    }
       
    public void writeToFile() throws IOException, WriteException{
    	wb.write();
        wb.close();    
    }
    
    private void createLabel(WritableSheet sheet)
            throws WriteException {

        // Write a few headers
        for(int i=0; i<header.length; i++){
        	addCaption(sheet, i, 0, header[i]);
        }
    }

    private void createContent(WritableSheet sheet, int row, int seed) throws WriteException,
            RowsExceededException {
    	
        // Write a few number
//        for (int i = 1; i < 10; i++) {
//            // First column
//            addNumber(sheet, 0, i, i + 10);
//            // Second column
//            addNumber(sheet, 1, i, i * i);
//        }
//        // Lets calculate the sum of it
//        StringBuffer buf = new StringBuffer();
//        buf.append("SUM(A2:A10)");
//        Formula f = new Formula(0, 10, buf.toString());
//        sheet.addCell(f);
//        buf = new StringBuffer();
//        buf.append("SUM(B2:B10)");
//        f = new Formula(1, 10, buf.toString());
//        sheet.addCell(f);

//        // now a bit of text
//        for (int i = 12; i < 20; i++) {
//            // First column
//            addLabel(sheet, 0, i, "Boring text " + i);
//            // Second column
//            addLabel(sheet, 1, i, "Another text");
//        }
        
        for (DTNHost h: nodeRecord.keySet()){
        	NodeProperties nProps = nodeRecord.get(h);
        	
        	addLabel(sheet, 0, row, h.toString());
        	addDouble(sheet, 1, row, nProps.getAverageWaitTime());
        	addDouble(sheet, 2, row, nProps.getTimeFirstRequested());
        	addDouble(sheet, 3, row, nProps.getTimeFirstChunkReceived());
        	addDouble(sheet, 4, row, nProps.getTimeStartedPlaying());
        	addDouble(sheet, 5, row, nProps.getTimeLastPlayed());
        	addLong(sheet, 6, row, nProps.getAck());
        	addLong(sheet, 7, row, nProps.getLastChunkReceived());
           	addDouble(sheet, 8, row, nProps.getNrofTimesInterrupted()/100);
           	addInteger(sheet, 9, row, nProps.getNrOfSkippedChunks());
        	addInteger(sheet, 10, row, nProps.getNrofChunksReceived());
        	addInteger(sheet, 11, row, nProps.getNrofDuplicateChunks());
        	addInteger(sheet, 12, row, nProps.getNrofTimesRequested());
        	addInteger(sheet, 13, row, nProps.getNrofDuplicateRequest());
        	addInteger(sheet, 14, row, nProps.getNrOfTimesSentIndex());
        	addInteger(sheet, 15, row, nProps.getNrofTimesSentTrans());
        	addInteger(sheet, 16, row, nProps.getNrOfTimesSentChunk());
        	addInteger(sheet, 17, row, nProps.getNrOfFragmentsCreated());
        	addInteger(sheet, 18, row, nProps.getSizeAdjustedCount());

        	//overhead pa for decoding buffermap and encoding fragments
        	addInteger(sheet, 18, row, seed);
        	row++;
        }
    }

    private void addCaption(WritableSheet sheet, int column, int row, String s)
            throws RowsExceededException, WriteException {
        Label label;
        label = new Label(column, row, s, timesBoldUnderline);
        sheet.addCell(label);
    }

    private void addInteger(WritableSheet sheet, int column, int row,
            Integer integer) throws WriteException, RowsExceededException {
        Number number;
        number = new Number(column, row, integer, times);
        sheet.addCell(number);
    }
    
    private void addDouble(WritableSheet sheet, int column, int row,
            Double value) throws WriteException, RowsExceededException {
        Number number;
        number = new Number(column, row, value, times);
        sheet.addCell(number);
    }
    
    private void addLong(WritableSheet sheet, int column, int row,
            long value) throws WriteException, RowsExceededException {
        Number number;
        number = new Number(column, row, value, times);
        sheet.addCell(number);
    }

    private void addLabel(WritableSheet sheet, int column, int row, String s)
            throws WriteException, RowsExceededException {
        Label label;
        label = new Label(column, row, s, times);
        sheet.addCell(label);
    }

}