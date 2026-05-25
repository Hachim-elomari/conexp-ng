package fcatools.conexpng.gui.thresholds;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
 
/**
 * Imports continuous values from Excel files (.xlsx, .xls).
 * 
 * Expected structure:
 * - Row 1: Header with attribute names (columns)
 * - Column 1: Object names (rows)
 * - Other cells: numeric values (continuous data)
 * 
 * Example:
 *        | Attribute1 | Attribute2 | Attribute3
 * -------+------------+------------+------------
 * Object1|    15.5    |    22.0    |    88.3
 * Object2|    30.1    |    45.5    |    12.0
 */
public class ExcelImporter {

    /**
     * Import Excel file and extract continuous values.
     * 
     * @param file Excel file (.xlsx or .xls)
     * @return ExcelData with attributes, objects, and values
     * @throws Exception if file is invalid or cannot be read
     */
    public static ExcelData importFile(File file) throws Exception {
        if (file == null || !file.exists()) {
            throw new Exception("File not found: " + file);
        }
        
        String fileName = file.getName().toLowerCase();
        if (!fileName.endsWith(".xlsx") && !fileName.endsWith(".xls")) {
            throw new Exception("Invalid file format. Expected .xlsx or .xls");
        }
        
        FileInputStream fis = null;
        Workbook workbook = null;
        
        try {
            fis = new FileInputStream(file);
            
            // Open workbook (auto-detect format)
            if (fileName.endsWith(".xlsx")) {
                workbook = new XSSFWorkbook(fis);
            } else {
                workbook = new HSSFWorkbook(fis);
            }
            
            // Check if workbook has sheets
            if (workbook.getNumberOfSheets() == 0) {
                throw new Exception("Excel file is empty (no sheets)");
            }
            
            // For now, always use first sheet
            // TODO: if multiple sheets, let user choose
            Sheet sheet = workbook.getSheetAt(0);
            
            return parseSheet(sheet);
            
        } finally {
            if (workbook != null) {
                try { workbook.close(); } catch (Exception ignored) {}
            }
            if (fis != null) {
                try { fis.close(); } catch (Exception ignored) {}
            }
        }
    }
    
    /**
     * Parse a single sheet and extract data.
     */
    private static ExcelData parseSheet(Sheet sheet) throws Exception {
        if (sheet.getPhysicalNumberOfRows() < 2) {
            throw new Exception("Sheet must have at least 2 rows (header + data)");
        }
        
        ExcelData data = new ExcelData();
        
        // ── Step 1: Read header (row 0) → attributes ─────────────────────────
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            throw new Exception("Header row (row 1) is empty");
        }
        
        List<String> attributes = new ArrayList<String>();
        int lastCol = headerRow.getLastCellNum();
        
        for (int colIdx = 1; colIdx < lastCol; colIdx++) {  // Start at 1 (skip col 0 = object names)
            Cell cell = headerRow.getCell(colIdx);
            String attrName = getCellStringValue(cell);
            
            if (attrName == null || attrName.trim().isEmpty()) {
                // Skip empty columns
                continue;
            }
            
            // ✅ Normalize to lowercase (Woman = woman = WOMAN)
            attributes.add(attrName.trim().toLowerCase());
        }
        
        if (attributes.isEmpty()) {
            throw new Exception("No attribute columns found in header");
        }
        
        data.attributes = attributes;
        
        // ── Step 2: Read data rows → objects + values ────────────────────────
        List<String> objects = new ArrayList<String>();
        Map<String, Map<String, Double>> values = new LinkedHashMap<String, Map<String, Double>>();
        
        int validCells = 0;
        int invalidCells = 0;
        
        for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {  // Start at 1 (skip header)
            Row row = sheet.getRow(rowIdx);
            if (row == null) continue;  // Skip empty rows
            
            // Column 0 = object name
            Cell objCell = row.getCell(0);
            String objName = getCellStringValue(objCell);
            
            if (objName == null || objName.trim().isEmpty()) {
                // Skip rows without object name
                continue;
            }
            
            // ✅ Normalize to lowercase (Female = female = FEMALE)
            objName = objName.trim().toLowerCase();
            objects.add(objName);
            
            Map<String, Double> objValues = new LinkedHashMap<String, Double>();
            
            // Read numeric values for each attribute
            for (int colIdx = 0; colIdx < attributes.size(); colIdx++) {
                String attr = attributes.get(colIdx);
                Cell cell = row.getCell(colIdx + 1);  // +1 because col 0 is object name
                
                Double value = getCellNumericValue(cell);
                
                if (value != null) {
                    objValues.put(attr, value);
                    validCells++;
                } else {
                    invalidCells++;
                }
            }
            
            values.put(objName, objValues);
        }
        
        if (objects.isEmpty()) {
            throw new Exception("No data rows found (all object names empty)");
        }
        
        data.objects = objects;
        data.values = values;
        data.validCells = validCells;
        data.invalidCells = invalidCells;
        
        return data;
    }
    
    /**
     * Extract string value from cell (for headers and object names).
     */
    private static String getCellStringValue(Cell cell) {
        if (cell == null) return null;
        
        int cellType = cell.getCellType();
        
        switch (cellType) {
            case Cell.CELL_TYPE_STRING:
                return cell.getStringCellValue();
            
            case Cell.CELL_TYPE_NUMERIC:
                // Convert number to string (e.g., "1.0" → "1")
                double num = cell.getNumericCellValue();
                if (num == (long) num) {
                    return String.valueOf((long) num);
                }
                return String.valueOf(num);
            
            case Cell.CELL_TYPE_BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            
            case Cell.CELL_TYPE_FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            
            case Cell.CELL_TYPE_BLANK:
            default:
                return null;
        }
    }
    
    /**
     * Extract numeric value from cell (for continuous data).
     */
    private static Double getCellNumericValue(Cell cell) {
        if (cell == null) return null;
        
        int cellType = cell.getCellType();
        
        switch (cellType) {
            case Cell.CELL_TYPE_NUMERIC:
                return cell.getNumericCellValue();
            
            case Cell.CELL_TYPE_STRING:
                // Try parsing string as number
                String str = cell.getStringCellValue().trim();
                if (str.isEmpty()) return null;
                
                try {
                    // Handle both formats: "1234.56" and "1234,56"
                    str = str.replace(",", ".");
                    return Double.parseDouble(str);
                } catch (NumberFormatException e) {
                    return null;  // Not a valid number
                }
            
            case Cell.CELL_TYPE_FORMULA:
                try {
                    return cell.getNumericCellValue();
                } catch (Exception e) {
                    return null;
                }
            
            case Cell.CELL_TYPE_BLANK:
            default:
                return null;
        }
    }
}