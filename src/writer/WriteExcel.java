package writer;

import java.io.File;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;

import core.DTNHost;
import jxl.CellView;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.format.UnderlineStyle;
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
    		"ACK", "LastChunkReceived", "#ofTimesInterrupted (seconds)", "TotalChunksReceived", "#ofDuplicateChunksReceived", "#ofTimesRequested", "#ofDuplicateRequest", 
                               "TotalIndexFragmentSent", "TotalTransFragmentSent", "TotalChunksSent", "#ofFragmentsCreated (IndexLevel)", "#ofTimesAdjusted"};
    HashMap<DTNHost, NodeProperties> nodeRecord = new HashMap<DTNHost, NodeProperties>();
    
    public void setOutputFile(String inputFile) {
    	this.inputFile = inputFile;
    }

    public void write(HashMap<DTNHost, NodeProperties> nodeRecord) throws IOException, WriteException {
        
    	File file = new File(inputFile);
        WorkbookSettings wbSettings = new WorkbookSettings();

        wbSettings.setLocale(new Locale("en", "EN"));

        WritableWorkbook workbook = Workbook.createWorkbook(file, wbSettings);
        workbook.createSheet("Report", 0);
        WritableSheet excelSheet = workbook.getSheet(0);
        
        this.nodeRecord = nodeRecord;
        
        createLabel(excelSheet);
        createContent(excelSheet);
 
        workbook.write();
        workbook.close();
    }

    private void createLabel(WritableSheet sheet)
            throws WriteException {
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

        // Write a few headers
        for(int i=0; i<header.length; i++){
        	addCaption(sheet, i, 0, header[i]);
        }
    }

    private void createContent(WritableSheet sheet) throws WriteException,
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
        
        int column=1;
        for (DTNHost h: nodeRecord.keySet()){
        	NodeProperties nProps = nodeRecord.get(h);
        	
        	addLabel(sheet, 0, column, h.toString());
        	addDouble(sheet, 1, column, nProps.getAverageWaitTime());
        	addDouble(sheet, 2, column, nProps.getTimeFirstRequested());
        	addDouble(sheet, 3, column, nProps.getTimeFirstChunkReceived());
        	addDouble(sheet, 4, column, nProps.getTimeStartedPlaying());
        	addDouble(sheet, 5, column, nProps.getTimeLastPlayed());
        	addLong(sheet, 6, column, nProps.getAck());
        	addLong(sheet, 7, column, nProps.getLastChunkReceived());
        	addDouble(sheet, 8, column, nProps.getNrofTimesInterrupted()/100);
        	addInteger(sheet, 9, column, nProps.getNrofChunksReceived());
        	addInteger(sheet, 10, column, nProps.getNrofDuplicateChunks());
        	addInteger(sheet, 11, column, nProps.getNrofTimesRequested());
        	addInteger(sheet, 12, column, nProps.getNrofDuplicateRequest());
        	addInteger(sheet, 13, column, nProps.getNrOfTimesSentIndex());
        	addInteger(sheet, 14, column, nProps.getNrofTimesSentTrans());
        	addInteger(sheet, 15, column, nProps.getNrOfTimesSentChunk());
        	addInteger(sheet, 16, column, nProps.getNrOfFragmentsCreated());
        	addInteger(sheet, 17, column, nProps.getSizeAdjustedCount());
        	//overhead pa for decoding buffermap and encoding fragments
        	
        	 column++;
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
    
    private void addLong(WritableSheet sheet, int column, int row,
           long value) throws WriteException, RowsExceededException {
        Number number;
        number = new Number(column, row, value, times);
        sheet.addCell(number);
    }
    
    private void addDouble(WritableSheet sheet, int column, int row,
            double value) throws WriteException, RowsExceededException {
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