package fcatools.conexpng.gui.contexteditor;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.table.*;

import com.alee.laf.scroll.WebScrollPane;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.Map;

import static fcatools.conexpng.Util.clamp;
import static javax.swing.KeyStroke.getKeyStroke;
import fcatools.conexpng.model.AttributeGroup;
import java.util.List;

public class ContextMatrix extends JTable {

    private static final long serialVersionUID = -7474568014425724962L;

    private static final Color HEADER_COLOR_START    = new Color(235, 235, 235);
    private static final Color HEADER_COLOR_END      = new Color(213, 213, 213);
    private static final Color GROUP_HEADER_COLOR    = new Color(200, 200, 200);
    private static final Color HEADER_SEPARATOR_COLOR = new Color(170, 170, 170);
    private static final Color DRAGGING_COLOR        = HEADER_COLOR_START;
    private static final Color EVEN_ROW_COLOR        = new Color(252, 252, 252);
    private static final Color ODD_ROW_COLOR         = new Color(255, 255, 255);
    private static final Color TABLE_GRID_COLOR      = new Color(120, 120, 120);

    // (F1) Couleurs pour la fusion des groupes
    private static final Color GROUP_BG     = new Color(180, 210, 255);
    private static final Color GROUP_FG     = new Color(0, 51, 102);
    private static final Color GROUP_BORDER = new Color(100, 150, 200);

    public ContextMatrix(TableModel dm, Map<Integer, Integer> columnWidths) {
        super(dm);
        this.columnWidths = columnWidths;
        setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        setTableHeader(null);
        setOpaque(false);
        setGridColor(TABLE_GRID_COLOR);
        setIntercellSpacing(new Dimension(0, 0));
        setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        setCellSelectionEnabled(true);
        setShowGrid(false);
        clearKeyBindings();
        createResizingInteractions();
        createDraggingInteractions();
        setupCustomRenderer();
    }

    private void setupCustomRenderer() {
        DefaultTableCellRenderer customRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(JLabel.CENTER);
                if (row == 0) {
                    setBackground(GROUP_HEADER_COLOR);
                    setFont(getFont().deriveFont(Font.BOLD));
                    setForeground(Color.BLACK);
                    setText(""); // (F1) Toujours vide : paint() s'en charge
                } else if (row == 1) {
                    setBackground(HEADER_COLOR_START);
                    setFont(getFont().deriveFont(Font.PLAIN));
                    setForeground(Color.BLACK);
                } else {
                    if (isSelected) {
                        setBackground(table.getSelectionBackground());
                        setForeground(table.getSelectionForeground());
                    } else {
                        setBackground((row % 2 == 0) ? EVEN_ROW_COLOR : ODD_ROW_COLOR);
                        setForeground(Color.BLACK);
                    }
                }
                setOpaque(true);
                return this;
            }
        };
        setDefaultRenderer(Object.class, customRenderer);
    }

    // =========================================================================
    // (F1) VRAIE FUSION : paint() dessine les cellules fusionnées par-dessus
    // =========================================================================

    @Override
    public void paint(Graphics g) {
        super.paint(g); // JTable dessine normalement d'abord

        // Ensuite on dessine les cellules fusionnées PAR-DESSUS
        if (getModel() instanceof ContextMatrixModel) {
            ContextMatrixModel model = (ContextMatrixModel) getModel();
            if (model.isDisplayWithGroups()) {
                paintMergedGroupCells((Graphics2D) g, model);
            }
        }
    }

    private void paintMergedGroupCells(Graphics2D g2d, ContextMatrixModel model) {
        int colCount = getColumnCount();
        int col = 1; // Ignorer colonne 0 (noms des objets)

        while (col < colCount) {
            Object val = model.getValueAt(0, col);
            String groupName = (val != null) ? val.toString().trim() : "";

            if (groupName.isEmpty()) {
                col++;
                continue;
            }

            // Trouver la dernière colonne de ce groupe
            int lastCol = col;
            for (int next = col + 1; next < colCount; next++) {
                Object nextVal = model.getValueAt(0, next);
                String nextGroup = (nextVal != null) ? nextVal.toString().trim() : "";
                if (nextGroup.equals(groupName)) {
                    lastCol = next;
                } else {
                    break;
                }
            }

            // Rectangle fusionné
            Rectangle r1 = getCellRect(0, col, true);
            Rectangle r2 = getCellRect(0, lastCol, true);
            int x      = r1.x;
            int y      = r1.y;
            int width  = r2.x + r2.width - r1.x;
            int height = r1.height;

            // Fond
            g2d.setColor(GROUP_BG);
            g2d.fillRect(x, y, width, height);

            // Bordure
            g2d.setColor(GROUP_BORDER);
            g2d.drawRect(x, y, width - 1, height - 1);

            // Texte centré
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                                 RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setFont(new Font("Arial", Font.BOLD, 12));
            g2d.setColor(GROUP_FG);
            FontMetrics fm = g2d.getFontMetrics();
            int textX = x + (width  - fm.stringWidth(groupName)) / 2;
            int textY = y + (height + fm.getAscent() - fm.getDescent()) / 2;
            g2d.drawString(groupName, textX, textY);

            col = lastCol + 1;
        }
    }

    // =========================================================================
    // Reste du code original inchangé
    // =========================================================================

    public WebScrollPane createStripedJScrollPane(Color bg) {
        WebScrollPane scrollPane = new WebScrollPane(this);
        scrollPane.setViewport(new StripedViewport(this, bg));
        scrollPane.getViewport().setView(this);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        return scrollPane;
    }

    @Override
    public void tableChanged(TableModelEvent e) {
        super.tableChanged(e);
        alignCells();
        restoreColumnWidths();
        makeHeaderCellsEditable();
    }

    private void clearKeyBindings() {
        int[] is = { JComponent.WHEN_IN_FOCUSED_WINDOW, JComponent.WHEN_FOCUSED,
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT };
        InputMap im = getInputMap();
        for (int i = 0; i <= is.length; i++) {
            im.put(getKeyStroke(KeyEvent.VK_ENTER, 0), "none");
            im.put(getKeyStroke(KeyEvent.VK_UP, 0), "none");
            im.put(getKeyStroke(KeyEvent.VK_LEFT, 0), "none");
            im.put(getKeyStroke(KeyEvent.VK_RIGHT, 0), "none");
            im.put(getKeyStroke(KeyEvent.VK_DOWN, 0), "none");
            im.put(getKeyStroke(KeyEvent.VK_UP, KeyEvent.SHIFT_MASK), "none");
            im.put(getKeyStroke(KeyEvent.VK_LEFT, KeyEvent.SHIFT_MASK), "none");
            im.put(getKeyStroke(KeyEvent.VK_RIGHT, KeyEvent.SHIFT_MASK), "none");
            im.put(getKeyStroke(KeyEvent.VK_DOWN, KeyEvent.SHIFT_MASK), "none");
            if (i == is.length) break;
            im = getInputMap(is[i]);
        }
    }

    private void alignCells() {
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        for (int i = 0; i < getColumnCount(); i++) {
            getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
        }
    }

    // Selecting
    private int lastSelectedRowsStartIndex;
    private int lastSelectedRowsEndIndex;
    private int lastSelectedColumnsStartIndex;
    public int lastSelectedColumnsEndIndex;

    public void selectCell(int row, int column) {
        row = clamp(row, 2, getRowCount() - 1);
        column = clamp(column, 1, getColumnCount() - 1);
        setRowSelectionInterval(row, row);
        setColumnSelectionInterval(column, column);
    }

    public void selectRow(int row) {
        setRowSelectionInterval(row, row);
        setColumnSelectionInterval(1, this.getColumnCount() - 1);
    }

    public void selectColumn(int column) {
        setColumnSelectionInterval(column, column);
        setRowSelectionInterval(2, this.getRowCount() - 1);
    }

    public void saveSelection() {
        lastSelectedRowsStartIndex = getSelectedRow();
        lastSelectedRowsEndIndex = getSelectedRowCount() - 1 + lastSelectedRowsStartIndex;
        lastSelectedColumnsStartIndex = getSelectedColumn();
        lastSelectedColumnsEndIndex = getSelectedColumnCount() - 1 + lastSelectedColumnsStartIndex;
    }

    public void restoreSelection() {
        if (getRowCount() <= 2 || getColumnCount() <= 1) return;
        if ((lastSelectedColumnsEndIndex <= 0 && lastSelectedColumnsStartIndex <= 0)
                || (lastSelectedRowsEndIndex <= 1 && lastSelectedRowsStartIndex <= 1)) return;
        lastSelectedRowsStartIndex    = clamp(lastSelectedRowsStartIndex, 2, getRowCount() - 1);
        lastSelectedRowsEndIndex      = clamp(lastSelectedRowsEndIndex, 2, getRowCount() - 1);
        lastSelectedColumnsStartIndex = clamp(lastSelectedColumnsStartIndex, 1, getColumnCount() - 1);
        lastSelectedColumnsEndIndex   = clamp(lastSelectedColumnsEndIndex, 1, getColumnCount() - 1);
        setRowSelectionInterval(lastSelectedRowsStartIndex, lastSelectedRowsEndIndex);
        setColumnSelectionInterval(lastSelectedColumnsStartIndex, lastSelectedColumnsEndIndex);
    }

    public boolean wasColumnSelected(int j) {
        return (lastSelectedColumnsEndIndex == j && lastSelectedColumnsStartIndex == j
                && Math.min(lastSelectedRowsStartIndex, lastSelectedRowsEndIndex) == 2
                && Math.max(lastSelectedRowsStartIndex, lastSelectedRowsEndIndex) == getRowCount() - 1);
    }

    public boolean wasRowSelected(int i) {
        return (lastSelectedRowsEndIndex == i && lastSelectedRowsStartIndex == i
                && Math.min(lastSelectedColumnsStartIndex, lastSelectedColumnsEndIndex) == 1
                && Math.max(lastSelectedColumnsStartIndex, lastSelectedColumnsEndIndex) == getColumnCount() - 1);
    }

    public boolean wasAllSelected() {
        return (Math.min(lastSelectedRowsEndIndex, lastSelectedRowsStartIndex) == 2
                && Math.max(lastSelectedRowsEndIndex, lastSelectedRowsStartIndex) == getRowCount() - 1
                && Math.min(lastSelectedColumnsStartIndex, lastSelectedColumnsEndIndex) == 1
                && Math.max(lastSelectedColumnsStartIndex, lastSelectedColumnsEndIndex) == getColumnCount() - 1);
    }

    @Override
    public void selectAll() {
        setRowSelectionInterval(2, getRowCount() - 1);
        setColumnSelectionInterval(1, getColumnCount() - 1);
    }

    @Override
    public boolean isCellSelected(int i, int j) {
        return i > 1 && j != 0 && super.isCellSelected(i, j);
    }

    @Override
    public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
        Component component = super.prepareRenderer(renderer, row, column);
        if (component instanceof JComponent) {
            ((JComponent) component).setOpaque(isCellSelected(row, column));
        }
        return component;
    }

    public int getLastSelectedRowsStartIndex()    { return lastSelectedRowsStartIndex; }
    public int getLastSelectedRowsEndIndex()      { return lastSelectedRowsEndIndex; }
    public int getLastSelectedColumnsStartIndex() { return lastSelectedColumnsStartIndex; }
    public int getLastSelectedColumnsEndIndex()   { return lastSelectedColumnsEndIndex; }

    // Renaming
    public boolean isRenaming = false;

    private void makeHeaderCellsEditable() {
        for (int i = 0; i < getColumnCount(); i++) {
            getColumnModel().getColumn(i).setCellEditor(editor);
        }
    }

    public JTextField renameColumnHeader(int i) {
        isRenaming = true;
        editCellAt(1, i);
        requestFocus();
        ContextCellEditor ed = (ContextCellEditor) editor;
        ed.getTextField().requestFocus();
        ed.getTextField().selectAll();
        return ed.getTextField();
    }

    public JTextField renameRowHeader(int i) {
        isRenaming = true;
        editCellAt(i, 0);
        requestFocus();
        ContextCellEditor ed = (ContextCellEditor) editor;
        ed.getTextField().requestFocus();
        ed.getTextField().selectAll();
        return ed.getTextField();
    }

    TableCellEditor editor = new ContextCellEditor(new JTextField());

    @SuppressWarnings("serial")
    public class ContextCellEditor extends DefaultCellEditor {

        int lastRow = 0;
        int lastColumn = 0;
        String lastName;
        ContextMatrixModel model = null;
        JTextField textField = null;

        public ContextCellEditor(JTextField textField) {
            super(textField);
            this.textField = textField;
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
                boolean isSelected, int row, int column) {
            JTextField f = (JTextField) super.getTableCellEditorComponent(
                    table, value, isSelected, row, column);
            model = (ContextMatrixModel) table.getModel();
            String text;
            if (column == 0) {
                text = model.getObjectNameAt(row - 2);
            } else {
                text = model.getAttributeNameAt(column - 1);
            }
            f.setText(text);
            lastName   = text;
            lastColumn = column;
            lastRow    = row;
            this.textField = f;
            return f;
        }

        @Override
        public Object getCellEditorValue() {
            String newName = super.getCellEditorValue().toString();
            if (lastColumn == 0) {
                model.renameObject(lastName, newName);
            } else {
                model.renameAttribute(lastName, newName);
            }
            ContextMatrix.this.isRenaming = false;
            return super.getCellEditorValue();
        }

        public JTextField getTextField() { return textField; }
    }

    // Dragging
    private static Cursor dragCursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
    boolean isDraggingRow    = false;
    boolean isDraggingColumn = false;
    boolean didReorderOccur  = false;
    int lastDraggedRowIndex;
    int lastDraggedColumnIndex;

    private void createDraggingInteractions() {
        MouseAdapter mouseAdapter = new MouseAdapter() {

            public void mousePressed(MouseEvent e) {
                int i = rowAtPoint(e.getPoint());
                int j = columnAtPoint(e.getPoint());
                lastDraggedRowIndex    = i;
                lastDraggedColumnIndex = j;
                if (!isResizing) {
                    if (SwingUtilities.isLeftMouseButton(e) && j == 0 && i > 1) {
                        isDraggingRow = true;
                        setCursor(dragCursor);
                    }
                    if (SwingUtilities.isLeftMouseButton(e) && (i == 0 || i == 1) && j > 0) {
                        isDraggingColumn = true;
                        setCursor(dragCursor);
                    }
                }
            }

            public void mouseDragged(MouseEvent e) {
                Reorderable model = (Reorderable) getModel();
                int i = rowAtPoint(e.getPoint());
                int j = columnAtPoint(e.getPoint());
                if (i < 0 || j < 0) return;
                if (isDraggingRow || isDraggingColumn) clearSelection();
                if (isDraggingRow && i != lastDraggedRowIndex && i > 1) {
                    model.reorderRows(lastDraggedRowIndex, i);
                    ((AbstractTableModel) getModel()).fireTableDataChanged();
                    lastDraggedRowIndex = i;
                    didReorderOccur = true;
                }
                if (isDraggingColumn && j != lastDraggedColumnIndex && j != 0) {
                    Rectangle selected = getCellRect(0, lastDraggedColumnIndex, false);
                    Rectangle r = getCellRect(0, j, false);
                    Point p = r.getLocation();
                    if ((j <= lastDraggedColumnIndex || e.getX() > p.x + r.width - selected.width)
                            && (j > lastDraggedColumnIndex || e.getX() < p.x + selected.width)) {
                        model.reorderColumns(lastDraggedColumnIndex, j);
                        switchColumnWidths(lastDraggedColumnIndex, j);
                        ((AbstractTableModel) model).fireTableDataChanged();
                        lastDraggedColumnIndex = j;
                        didReorderOccur = true;
                    }
                }
            }

            public void mouseReleased(MouseEvent e) {
                int i = rowAtPoint(e.getPoint());
                int j = columnAtPoint(e.getPoint());
                if (!isResizing && !didReorderOccur) {
                    if (SwingUtilities.isLeftMouseButton(e) && j == 0 && i > 1) {
                        if (!wasRowSelected(i)) selectRow(i);
                        else clearSelection();
                    }
                    if (SwingUtilities.isLeftMouseButton(e) && (i == 0 || i == 1) && j > 0) {
                        if (!wasColumnSelected(j)) selectColumn(j);
                        else clearSelection();
                    }
                    if (SwingUtilities.isLeftMouseButton(e) && (i == 0 || i == 1) && j == 0) {
                        if (!wasAllSelected()) selectAll();
                        else clearSelection();
                    }
                }
                if ((isDraggingRow || isDraggingColumn) && getCursor().equals(dragCursor))
                    setCursor(Cursor.getDefaultCursor());
                isDraggingRow    = false;
                isDraggingColumn = false;
                didReorderOccur  = false;
                isResizing       = false;
                saveSelection();
                invalidate();
                repaint();
            }
        };
        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);
    }

    // Resizing
    public static final int DEFAULT_COLUMN_WIDTH   = 80;
    public static final int COMPACTED_COLUMN_WIDTH = 15;
    private static Cursor resizeCursor = Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
    public Map<Integer, Integer> columnWidths;
    public Map<Integer, Integer> compactedColumnWidths = new HashMap<Integer, Integer>();
    private int mouseXOffset;
    private TableColumn resizingColumn;
    private boolean isCompacted = false;
    private boolean isResizing  = false;

    public void compact() {
        isCompacted = true;
        for (int i = 1; i < getColumnModel().getColumnCount(); i++) {
            getColumnModel().getColumn(i).setPreferredWidth(COMPACTED_COLUMN_WIDTH);
        }
        compactedColumnWidths.clear();
    }

    public void uncompact() {
        isCompacted = false;
        restoreColumnWidths();
    }

    public void loadColumnWidths(Map<Integer, Integer> loadedColumnWidths) {
        compactedColumnWidths.clear();
        columnWidths = loadedColumnWidths;
        restoreColumnWidths();
    }

    public void restoreColumnWidths() {
        if (columnWidths == null) return;
        if (!isCompacted) {
            for (int i = 0; i < getColumnCount(); i++) {
                Integer w = columnWidths.get(i);
                TableColumn t = getColumnModel().getColumn(i);
                t.setPreferredWidth(w == null ? DEFAULT_COLUMN_WIDTH : w);
            }
        } else {
            for (int i = 1; i < getColumnCount(); i++) {
                Integer w = compactedColumnWidths.get(i);
                TableColumn t = getColumnModel().getColumn(i);
                t.setPreferredWidth(w == null ? COMPACTED_COLUMN_WIDTH : w);
            }
        }
    }

    public void updateColumnWidths(int removedIndex) {
        if (!isCompacted) {
            columnWidths.remove(removedIndex);
            for (int i = removedIndex + 1; i < getColumnCount() + 20; i++) {
                Integer oldWidth = columnWidths.remove(i);
                if (oldWidth == null) continue;
                if (i == 1) continue;
                columnWidths.put(i - 1, oldWidth);
            }
        } else {
            compactedColumnWidths.remove(removedIndex);
            for (int i = removedIndex + 1; i < getColumnCount() + 20; i++) {
                Integer oldWidth = compactedColumnWidths.remove(i);
                if (oldWidth == null) continue;
                if (i == 1) continue;
                compactedColumnWidths.put(i - 1, oldWidth);
            }
        }
    }

    public void switchColumnWidths(int from, int to) {
        Integer fromVal = columnWidths.get(from);
        Integer toVal   = columnWidths.get(to);
        if (fromVal == null) fromVal = DEFAULT_COLUMN_WIDTH;
        if (toVal   == null) toVal   = DEFAULT_COLUMN_WIDTH;
        columnWidths.put(to, fromVal);
        columnWidths.put(from, toVal);

        fromVal = compactedColumnWidths.get(from);
        toVal   = compactedColumnWidths.get(to);
        if (fromVal == null) fromVal = COMPACTED_COLUMN_WIDTH;
        if (toVal   == null) toVal   = COMPACTED_COLUMN_WIDTH;
        compactedColumnWidths.put(to, fromVal);
        compactedColumnWidths.put(from, toVal);
    }

    private void createResizingInteractions() {
        MouseAdapter columnResizeMouseAdapter = new MouseAdapter() {
            ContextMatrix matrix = ContextMatrix.this;

            public void mousePressed(MouseEvent e) {
                Point p = e.getPoint();
                int index = matrix.columnAtPoint(p);
                if (index == -1) return;
                TableColumn resizingColumn = getResizingColumn(p, index);
                if (resizingColumn == null) return;
                matrix.resizingColumn = resizingColumn;
                mouseXOffset = p.x - resizingColumn.getWidth();
                matrix.restoreSelection();
                isResizing = true;
            }

            public void mouseMoved(MouseEvent e) {
                if (getResizingColumn(e.getPoint()) != null) setCursor(resizeCursor);
                else setCursor(Cursor.getDefaultCursor());
            }

            public void mouseDragged(MouseEvent e) {
                int mouseX = e.getX();
                TableColumn resizingColumn = matrix.resizingColumn;
                if (resizingColumn != null) {
                    matrix.restoreSelection();
                    int oldWidth = resizingColumn.getWidth();
                    int newWidth = Math.max(mouseX - mouseXOffset, 20);
                    resizingColumn.setWidth(newWidth);
                    resizingColumn.setPreferredWidth(newWidth);
                    if (!isCompacted) columnWidths.put(resizingColumn.getModelIndex(), newWidth);
                    else compactedColumnWidths.put(resizingColumn.getModelIndex(), newWidth);
                    restoreSelection();

                    Container container;
                    if ((matrix.getParent() == null)
                            || ((container = matrix.getParent().getParent()) == null)
                            || !(container instanceof JScrollPane)) return;

                    JViewport viewport = ((JScrollPane) container).getViewport();
                    int viewportWidth  = viewport.getWidth();
                    int diff           = newWidth - oldWidth;
                    int newHeaderWidth = matrix.getWidth() + diff;

                    Dimension tableSize = matrix.getSize();
                    tableSize.width += diff;
                    matrix.setSize(tableSize);

                    if ((newHeaderWidth >= viewportWidth)
                            && (matrix.getAutoResizeMode() == JTable.AUTO_RESIZE_OFF)) {
                        Point p = viewport.getViewPosition();
                        p.x = Math.max(0, Math.min(newHeaderWidth - viewportWidth, p.x + diff));
                        viewport.setViewPosition(p);
                        mouseXOffset += diff;
                    }
                }
            }

            public void mouseReleased(MouseEvent e) {
                matrix.resizingColumn = null;
                if (isResizing && getCursor().equals(resizeCursor))
                    setCursor(Cursor.getDefaultCursor());
            }

            private TableColumn getResizingColumn(Point p) {
                return getResizingColumn(p, columnAtPoint(p));
            }

            private TableColumn getResizingColumn(Point p, int column) {
                if (column == -1) return null;
                int row = rowAtPoint(p);
                if (row != 0 && row != 1) return null;
                Rectangle r = getCellRect(row, column, true);
                r.grow(-3, 0);
                if (r.contains(p)) return null;
                int midPoint   = r.x + r.width / 2;
                int columnIndex = (p.x < midPoint) ? column - 1 : column;
                if (columnIndex == -1) return null;
                return getColumnModel().getColumn(columnIndex);
            }
        };
        addMouseListener(columnResizeMouseAdapter);
        addMouseMotionListener(columnResizeMouseAdapter);
    }

    // Drawing
    private class StripedViewport extends JViewport {

        private static final long serialVersionUID = 171992496170114834L;

        private final Color BACKGROUND_COLOR;
        private final JTable fTable;

        public StripedViewport(JTable table, Color bg) {
            BACKGROUND_COLOR = bg;
            fTable = table;
            setBackground(BACKGROUND_COLOR);
            setOpaque(false);
            initListeners();
        }

        private void initListeners() {
            PropertyChangeListener listener = createTableColumnWidthListener();
            for (int i = 0; i < fTable.getColumnModel().getColumnCount(); i++) {
                fTable.getColumnModel().getColumn(i).addPropertyChangeListener(listener);
            }
        }

        private PropertyChangeListener createTableColumnWidthListener() {
            return new PropertyChangeListener() {
                public void propertyChange(PropertyChangeEvent evt) { repaint(); }
            };
        }

        @Override
        public void setViewPosition(Point p) {
            super.setViewPosition(p);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            paintBackground(g);
            paintStripedBackground(g);
            paintVerticalHeaderBackground(g);
            paintHorizontalHeaderBackground(g);
            paintGridLines(g);
            super.paintComponent(g);
        }

        private void paintBackground(Graphics g) {
            g.setColor(BACKGROUND_COLOR);
            g.fillRect(g.getClipBounds().x, g.getClipBounds().y,
                       g.getClipBounds().width, g.getClipBounds().height);
        }

        private void paintStripedBackground(Graphics g) {
            int rowHeight  = fTable.getRowHeight();
            int tableWidth = fTable.getWidth();
            int offsetX    = getViewPosition().x;
            int offsetY    = getViewPosition().y;
            int x = -offsetX;
            int y = -offsetY;
            for (int j = 2; j < fTable.getRowCount(); j++) {
                g.setColor((j % 2 == 0) ? EVEN_ROW_COLOR : ODD_ROW_COLOR);
                g.fillRect(x, y + j * rowHeight, tableWidth, rowHeight);
            }
        }

        private void paintVerticalHeaderBackground(Graphics g0) {
            Graphics2D g = (Graphics2D) g0;
            int firstColumnWidth = fTable.getColumnModel().getColumn(0).getWidth();
            int rowHeight = fTable.getRowHeight();
            int offsetX   = getViewPosition().x;
            int offsetY   = getViewPosition().y;
            int x = -offsetX;
            int y = -offsetY;

            GradientPaint gp = new GradientPaint(0, 0, HEADER_COLOR_END,
                                                 firstColumnWidth, 0, HEADER_COLOR_START);
            g.setPaint(gp);
            for (int j = 2; j < fTable.getRowCount(); j++) {
                g.fillRect(x, y + j * rowHeight, firstColumnWidth, rowHeight);
            }
            if (isDraggingRow) {
                g.setColor(new Color(230, 230, 230));
                g.fillRect(x, y + lastDraggedRowIndex * rowHeight + 1, firstColumnWidth, rowHeight - 1);
            }
            g.setColor(HEADER_SEPARATOR_COLOR);
            for (int j = 2; j < fTable.getRowCount() + 1; j++) {
                g.drawLine(x + 3, y + j * rowHeight, x + firstColumnWidth - 4, y + j * rowHeight);
            }
        }

        private void paintHorizontalHeaderBackground(Graphics g0) {
            Graphics2D g = (Graphics2D) g0;
            int tableWidth = fTable.getWidth();
            int rowHeight  = fTable.getRowHeight();
            int offsetX    = getViewPosition().x;
            int offsetY    = getViewPosition().y;
            int x = -offsetX;
            int y = -offsetY;

            // Ligne 0 : Noms des GROUPES
            g.setColor(GROUP_HEADER_COLOR);
            g.fillRect(x, y, tableWidth, rowHeight);

            // Ligne 1 : Noms des ATTRIBUTS
            GradientPaint gp = new GradientPaint(0, rowHeight, HEADER_COLOR_START,
                                                 0, 2 * rowHeight, HEADER_COLOR_END);
            g.setPaint(gp);
            g.fillRect(x, y + rowHeight, tableWidth, rowHeight);

            if (isDraggingColumn) {
                int columnWidth0 = 0;
                int columnWidth1 = fTable.getColumnModel().getColumn(lastDraggedColumnIndex).getWidth();
                for (int j = 1; j < lastDraggedColumnIndex + 1; j++) {
                    columnWidth0 += fTable.getColumnModel().getColumn(j - 1).getWidth();
                }
                g.setColor(DRAGGING_COLOR);
                g.fillRect(x + columnWidth0 - 2, y, columnWidth1, 2 * rowHeight - 1);
            }

            g.setColor(HEADER_SEPARATOR_COLOR);
            int columnWidth = 0;
            for (int j = 1; j < fTable.getColumnCount() + 1; j++) {
                columnWidth += fTable.getColumnModel().getColumn(j - 1).getWidth();
                g.drawLine(x + columnWidth - 1, y + 1, x + columnWidth - 1, y + 2 * rowHeight - 3);
            }
            g.setColor(HEADER_SEPARATOR_COLOR);
            g.drawLine(x, y + rowHeight, x + tableWidth, y + rowHeight);
        }

        private void paintGridLines(Graphics g) {
            int tableHeight      = fTable.getHeight();
            int rowHeight        = fTable.getRowHeight();
            int firstColumnWidth = fTable.getColumnModel().getColumn(0).getWidth();
            int offsetX          = getViewPosition().x;
            int offsetY          = getViewPosition().y;
            int x = -offsetX;
            int y = -offsetY;
            g.setColor(TABLE_GRID_COLOR);

            for (int i = 0; i < fTable.getColumnCount(); i++) {
                TableColumn column = fTable.getColumnModel().getColumn(i);
                x += column.getWidth();
                g.drawLine(x - 1, y + 2 * rowHeight, x - 1, y + tableHeight);
            }
            g.drawLine(x - 1, y, x - 1, y + tableHeight);

            for (int j = 2; j < fTable.getRowCount() + 1; j++) {
                g.drawLine(-offsetX + firstColumnWidth, y + j * rowHeight, x - 1, y + j * rowHeight);
            }
            g.drawLine(-offsetX, y + fTable.getRowCount() * rowHeight,
                        x - 1,  y + fTable.getRowCount() * rowHeight);
        }
    }
}