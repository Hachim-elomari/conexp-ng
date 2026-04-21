package fcatools.conexpng.gui.contexteditor;

import static fcatools.conexpng.Util.addMenuItem;
import static fcatools.conexpng.Util.clamp;
import static fcatools.conexpng.Util.createButton;
import static fcatools.conexpng.Util.createToggleButton;
import static fcatools.conexpng.Util.invokeAction;
import static fcatools.conexpng.Util.mod;
import static javax.swing.KeyStroke.getKeyStroke;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

import com.alee.extended.panel.WebButtonGroup;
import com.alee.laf.button.WebToggleButton;
import com.alee.laf.menu.WebPopupMenu;

import de.tudresden.inf.tcs.fcaapi.exception.IllegalObjectException;
import de.tudresden.inf.tcs.fcalib.FullObject;
import fcatools.conexpng.Conf;
import fcatools.conexpng.Conf.ContextChangeEvent;
import fcatools.conexpng.ContextChangeEvents;
import fcatools.conexpng.gui.StatusBarPropertyChangeListener;
import fcatools.conexpng.gui.View;
import fcatools.conexpng.gui.workers.ClarificationReductionWorker;
import fcatools.conexpng.io.locale.LocaleHandler;
import fcatools.conexpng.model.AttributeGroup;

@SuppressWarnings("serial")
public class ContextEditor extends View {

    private static final long serialVersionUID = 1660117627650529212L;
    private static final int MASK = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

    private final ContextMatrix matrix;
    private final ContextMatrixModel matrixModel;

    final WebPopupMenu cellPopupMenu;
    final WebPopupMenu objectCellPopupMenu;
    final WebPopupMenu attributeCellPopupMenu;
    final WebPopupMenu groupHeaderPopupMenu;

    WebToggleButton compactMatrixButton, showArrowRelationsButton;

    int lastActiveRowIndex;
    int lastActiveColumnIndex;

    public ContextEditor(final Conf state) {
        super(state);
        setLayout(new BorderLayout());

        cellPopupMenu          = new WebPopupMenu();
        objectCellPopupMenu    = new WebPopupMenu();
        attributeCellPopupMenu = new WebPopupMenu();
        groupHeaderPopupMenu   = new WebPopupMenu();

        matrixModel = new ContextMatrixModel(state);
        matrix      = new ContextMatrix(matrixModel, state.guiConf.columnWidths);
        JScrollPane scrollPane = matrix.createStripedJScrollPane(getBackground());
        scrollPane.setBorder(new EmptyBorder(3, 3, 3, 3));
        toolbar.setFloatable(false);
        panel.setLayout(new BorderLayout());
        panel.add(toolbar, BorderLayout.WEST);
        panel.add(scrollPane, BorderLayout.CENTER);
        add(panel);
        setBorder(new EmptyBorder(3, -3, -4, -3));
        toolbar.setRound(4);

        registerActions();
        createMouseActions();
        createKeyActions();
        createButtonActions();
        createContextMenuActions();
        scrollPane.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                invokeAction(ContextEditor.this, matrix.getActionMap().get("selectNone"));
                matrix.saveSelection();
            }
        });

        matrixModel.fireTableStructureChanged();
    }

    @Override
    public void propertyChange(PropertyChangeEvent e) {
        if (e instanceof ContextChangeEvent) {
            ContextChangeEvent cce = (ContextChangeEvent) e;
            if (cce.getName() == ContextChangeEvents.CONTEXTCHANGED) {
                matrixModel.fireTableStructureChanged();
                matrix.invalidate();
                matrix.repaint();
                matrix.restoreSelection();
            } else if (cce.getName() == ContextChangeEvents.NEWCONTEXT
                    || cce.getName() == ContextChangeEvents.LOADEDFILE) {
                matrixModel.loadNewContext(state);
                matrix.loadColumnWidths(state.guiConf.columnWidths);
                matrixModel.fireTableStructureChanged();
                matrix.invalidate();
                matrix.repaint();
            }
        }
    }

    private void registerActions() {
        ActionMap am = matrix.getActionMap();

        am.put("up",    new MoveAction(0, -1));
        am.put("down",  new MoveAction(0, +1));
        am.put("left",  new MoveAction(-1, 0));
        am.put("right", new MoveAction(+1, 0));
        am.put("upCarry",    new MoveWithCarryAction(0, -1));
        am.put("downCarry",  new MoveWithCarryAction(0, +1));
        am.put("leftCarry",  new MoveWithCarryAction(-1, 0));
        am.put("rightCarry", new MoveWithCarryAction(+1, 0));
        am.put("gotoFirstObject",    new MoveAction(0, -10000));
        am.put("gotoLastObject",     new MoveAction(0, +10000));
        am.put("gotoFirstAttribute", new MoveAction(-10000, 0));
        am.put("gotoLastAttribute",  new MoveAction(+10000, 0));
        am.put("moveObjectUp",       new MoveObjectOrAttributeAction(0, -1));
        am.put("moveObjectDown",     new MoveObjectOrAttributeAction(0, +1));
        am.put("moveAttributeLeft",  new MoveObjectOrAttributeAction(-1, 0));
        am.put("moveAttributeRight", new MoveObjectOrAttributeAction(+1, 0));

        am.put("selectAll",   new SelectAllAction());
        am.put("selectNone",  new SelectNoneAction());
        am.put("selectUp",    new ExpandSelectionAction(0, -1));
        am.put("selectDown",  new ExpandSelectionAction(0, +1));
        am.put("selectLeft",  new ExpandSelectionAction(-1, 0));
        am.put("selectRight", new ExpandSelectionAction(+1, 0));

        am.put("renameObject",             new RenameActiveObjectAction());
        am.put("renameAttribute",          new RenameActiveAttributeAction());
        am.put("removeObject",             new RemoveActiveObjectAction());
        am.put("removeSelectedObjects",    new RemoveSelectedObjectsAction());
        am.put("removeAttribute",          new RemoveActiveAttributeAction());
        am.put("removeSelectedAttributes", new RemoveSelectedAttributesAction());
        am.put("addObjectBelow",    new AddObjectAfterActiveAction());
        am.put("addObjectAbove",    new AddObjectBeforeActiveAction());
        am.put("addObjectAtEnd",    new AddObjectAtEndAction());
        am.put("addAttributeRight", new AddAttributeAfterActiveAction());
        am.put("addAttributeLeft",  new AddAttributeBeforeActiveAction());
        am.put("addAttributeAtEnd", new AddAttributeAtEndAction());

        am.put("toggle",  new ToggleActiveAction());
        am.put("invert",  new InvertAction());
        am.put("fill",    new FillAction());
        am.put("clear",   new ClearAction());

        am.put("clarifyObjects",    new ClarifyObjectsAction());
        am.put("clarifyAttributes", new ClarifyAttributesAction());
        am.put("reduceObjects",     new ReduceObjectsAction());
        am.put("reduceAttributes",  new ReduceAttributesAction());
        am.put("reduce",    new ReduceAction());
        am.put("transpose", new TransposeAction());
        am.put("compact",   new CompactAction());

        am.put("groupSelectedAttributes",    new GroupSelectedAttributesAction());
        am.put("ungroupAttribute",           new UngroupAttributeAction());
        am.put("renameGroup",                new RenameGroupAction());
        am.put("generateLatticeForGroup",    new GenerateLatticeForGroupAction());
        am.put("toggleGroupExpansion",       new ToggleGroupExpansionAction());
        am.put("deleteGroupWithAttributes",  new DeleteGroupWithAttributesAction());
        am.put("addAttributesToGroup",       new AddAttributesToGroupAction());
        // (F1) NOUVEAU
        am.put("removeAttributesFromGroup",  new RemoveAttributesFromGroupAction());
    }

    private void createKeyActions() {
        InputMap im = matrix.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        im.put(getKeyStroke(KeyEvent.VK_UP,    0), "up");
        im.put(getKeyStroke(KeyEvent.VK_DOWN,  0), "down");
        im.put(getKeyStroke(KeyEvent.VK_LEFT,  0), "left");
        im.put(getKeyStroke(KeyEvent.VK_RIGHT, 0), "right");
        im.put(getKeyStroke(KeyEvent.VK_K, 0), "upCarry");
        im.put(getKeyStroke(KeyEvent.VK_J, 0), "downCarry");
        im.put(getKeyStroke(KeyEvent.VK_H, 0), "leftCarry");
        im.put(getKeyStroke(KeyEvent.VK_L, 0), "rightCarry");
        im.put(getKeyStroke(KeyEvent.VK_G, 0), "gotoFirstObject");
        im.put(getKeyStroke(KeyEvent.VK_G, KeyEvent.SHIFT_MASK), "gotoLastObject");
        im.put(getKeyStroke(KeyEvent.VK_0, 0), "gotoFirstAttribute");
        im.put(getKeyStroke(KeyEvent.VK_DOLLAR, 0), "gotoLastAttribute");
        im.put(getKeyStroke(KeyEvent.VK_UP,    MASK), "moveObjectUp");
        im.put(getKeyStroke(KeyEvent.VK_DOWN,  MASK), "moveObjectDown");
        im.put(getKeyStroke(KeyEvent.VK_LEFT,  MASK), "moveAttributeLeft");
        im.put(getKeyStroke(KeyEvent.VK_RIGHT, MASK), "moveAttributeRight");
        im.put(getKeyStroke(KeyEvent.VK_K, MASK), "moveObjectUp");
        im.put(getKeyStroke(KeyEvent.VK_J, MASK), "moveObjectDown");
        im.put(getKeyStroke(KeyEvent.VK_H, MASK), "moveAttributeLeft");
        im.put(getKeyStroke(KeyEvent.VK_L, MASK), "moveAttributeRight");

        im.put(getKeyStroke(KeyEvent.VK_A, MASK), "selectAll");
        im.put(getKeyStroke(KeyEvent.VK_ESCAPE, 0), "selectNone");
        im.put(getKeyStroke(KeyEvent.VK_UP,    KeyEvent.SHIFT_MASK), "selectUp");
        im.put(getKeyStroke(KeyEvent.VK_DOWN,  KeyEvent.SHIFT_MASK), "selectDown");
        im.put(getKeyStroke(KeyEvent.VK_LEFT,  KeyEvent.SHIFT_MASK), "selectLeft");
        im.put(getKeyStroke(KeyEvent.VK_RIGHT, KeyEvent.SHIFT_MASK), "selectRight");
        im.put(getKeyStroke(KeyEvent.VK_K, KeyEvent.SHIFT_MASK), "selectUp");
        im.put(getKeyStroke(KeyEvent.VK_J, KeyEvent.SHIFT_MASK), "selectDown");
        im.put(getKeyStroke(KeyEvent.VK_H, KeyEvent.SHIFT_MASK), "selectLeft");
        im.put(getKeyStroke(KeyEvent.VK_L, KeyEvent.SHIFT_MASK), "selectRight");

        im.put(getKeyStroke(KeyEvent.VK_R, 0), "renameObject");
        im.put(getKeyStroke(KeyEvent.VK_R, KeyEvent.SHIFT_MASK), "renameAttribute");
        im.put(getKeyStroke(KeyEvent.VK_D, 0), "removeObject");
        im.put(getKeyStroke(KeyEvent.VK_D, KeyEvent.SHIFT_MASK), "removeAttribute");
        im.put(getKeyStroke(KeyEvent.VK_O, 0), "addObjectBelow");
        im.put(getKeyStroke(KeyEvent.VK_O, KeyEvent.SHIFT_MASK), "addObjectAbove");
        im.put(getKeyStroke(KeyEvent.VK_A, 0), "addAttributeRight");
        im.put(getKeyStroke(KeyEvent.VK_A, KeyEvent.SHIFT_MASK), "addAttributeLeft");

        im.put(getKeyStroke(KeyEvent.VK_ENTER, 0), "toggle");
        im.put(getKeyStroke(KeyEvent.VK_T, 0), "toggle");
        im.put(getKeyStroke(KeyEvent.VK_I, 0), "invert");
        im.put(getKeyStroke(KeyEvent.VK_F, 0), "fill");
        im.put(getKeyStroke(KeyEvent.VK_C, 0), "clear");
    }

    private void createButtonActions() {
        ActionMap am = matrix.getActionMap();
        WebButtonGroup group;
        group = new WebButtonGroup(WebButtonGroup.VERTICAL, true,
            createButton(LocaleHandler.getString("ContextEditor.createButtonActions.addObject"),
                "addObject", "icons/context editor/add_object.png", am.get("addObjectAtEnd")),
            createButton(LocaleHandler.getString("ContextEditor.createButtonActions.clarifyObjects"),
                "clarifyObjects", "icons/context editor/clarify_objects.png", am.get("clarifyObjects")),
            createButton(LocaleHandler.getString("ContextEditor.createButtonActions.reduceObjects"),
                "reduceObjects", "icons/context editor/reduce_objects.png", am.get("reduceObjects")));
        group.setButtonsDrawFocus(false);
        toolbar.add(group);
        toolbar.addSeparator();

        group = new WebButtonGroup(WebButtonGroup.VERTICAL, true,
            createButton(LocaleHandler.getString("ContextEditor.createButtonActions.addAttribute"),
                "addAttribute", "icons/context editor/add_attribute.png", am.get("addAttributeAtEnd")),
            createButton(LocaleHandler.getString("ContextEditor.createButtonActions.clarifyAttributes"),
                "clarifyAttributes", "icons/context editor/clarify_attributes.png", am.get("clarifyAttributes")),
            createButton(LocaleHandler.getString("ContextEditor.createButtonActions.reduceAttributes"),
                "reduceAttributes", "icons/context editor/reduce_attributes.png", am.get("reduceAttributes")));
        group.setButtonsDrawFocus(false);
        toolbar.add(group);
        toolbar.addSeparator();

        group = new WebButtonGroup(WebButtonGroup.VERTICAL, true,
            createButton(LocaleHandler.getString("ContextEditor.createButtonActions.reduceContext"),
                "reduceContext", "icons/context editor/reduce_context.png", am.get("reduce")),
            createButton(LocaleHandler.getString("ContextEditor.createButtonActions.transposeContext"),
                "transposeContext", "icons/context editor/transpose.png", am.get("transpose")));
        group.setButtonsDrawFocus(false);
        toolbar.add(group);
        toolbar.addSeparator();

        compactMatrixButton = createToggleButton(
            LocaleHandler.getString("ContextEditor.createButtonActions.compactMatrix"),
            "compactMatrix", "icons/context editor/compact.png", (ItemListener) am.get("compact"));
        showArrowRelationsButton = createToggleButton(
            LocaleHandler.getString("ContextEditor.createButtonActions.showArrowRelations"),
            "showArrowRelations", "icons/context editor/show_arrow_relations.png", null);
        updateButtonSelection();
        group = new WebButtonGroup(WebButtonGroup.VERTICAL, false,
            compactMatrixButton, showArrowRelationsButton);
        group.setButtonsDrawFocus(false);
        toolbar.add(group);

        toolbar.addSeparator();
        group = new WebButtonGroup(WebButtonGroup.VERTICAL, true,
            createButton("Group Attributes", "groupAttributes",
                "icons/context editor/reduce_context.png", am.get("groupSelectedAttributes")),
            createButton("Ungroup", "ungroup",
                "icons/context editor/transpose.png", am.get("ungroupAttribute")),
            createButton("Lattice for Group", "latticeForGroup",
                "icons/context editor/add_attribute.png", am.get("generateLatticeForGroup")));
        group.setButtonsDrawFocus(false);
        toolbar.add(group);
    }

    public void updateButtonSelection() {
        if (state.guiConf.compactMatrix) {
            compactMatrixButton.setSelected(true);
            matrix.compact();
        } else {
            compactMatrixButton.setSelected(false);
            matrix.uncompact();
        }
        if (state.guiConf.showArrowRelations) showArrowRelationsButton.setSelected(true);
        else showArrowRelationsButton.setSelected(false);
    }

    private void createContextMenuActions() {
        ActionMap am = matrix.getActionMap();

        addMenuItem(groupHeaderPopupMenu, "Rename Group",                  am.get("renameGroup"));
        addMenuItem(groupHeaderPopupMenu, "Generate Lattice for Group",    am.get("generateLatticeForGroup"));
        addMenuItem(groupHeaderPopupMenu, "Add Attribute(s) to this Group", am.get("addAttributesToGroup"));
        // (F1) NOUVEAU
        addMenuItem(groupHeaderPopupMenu, "Remove Attribute(s) from this Group", am.get("removeAttributesFromGroup"));
        groupHeaderPopupMenu.add(new WebPopupMenu.Separator());
        addMenuItem(groupHeaderPopupMenu, "Ungroup (keep attributes)",     am.get("ungroupAttribute"));
        addMenuItem(groupHeaderPopupMenu, "Delete Group + Attributes",     am.get("deleteGroupWithAttributes"));

        addMenuItem(cellPopupMenu,
            LocaleHandler.getString("ContextEditor.createContextMenuActions.selectAll"), am.get("selectAll"));
        cellPopupMenu.add(new WebPopupMenu.Separator());
        addMenuItem(cellPopupMenu,
            LocaleHandler.getString("ContextEditor.createContextMenuActions.fill"), am.get("fill"));
        addMenuItem(cellPopupMenu,
            LocaleHandler.getString("ContextEditor.createContextMenuActions.clear"), am.get("clear"));
        addMenuItem(cellPopupMenu,
            LocaleHandler.getString("ContextEditor.createContextMenuActions.invert"), am.get("invert"));
        cellPopupMenu.add(new WebPopupMenu.Separator());
        addMenuItem(cellPopupMenu,
            LocaleHandler.getString("ContextEditor.createContextMenuActions.removeSelectedAttributes"),
            am.get("removeSelectedAttributes"));
        addMenuItem(cellPopupMenu,
            LocaleHandler.getString("ContextEditor.createContextMenuActions.removeSelectedObjects"),
            am.get("removeSelectedObjects"));

        addMenuItem(objectCellPopupMenu,
            LocaleHandler.getString("ContextEditor.createContextMenuActions.renameObject"), am.get("renameObject"));
        addMenuItem(objectCellPopupMenu,
            LocaleHandler.getString("ContextEditor.createContextMenuActions.removeObject"), am.get("removeObject"));
        addMenuItem(objectCellPopupMenu,
            LocaleHandler.getString("ContextEditor.createContextMenuActions.addObjectAbove"), am.get("addObjectAbove"));
        addMenuItem(objectCellPopupMenu,
            LocaleHandler.getString("ContextEditor.createContextMenuActions.addObjectBelow"), am.get("addObjectBelow"));

        addMenuItem(attributeCellPopupMenu,
            LocaleHandler.getString("ContextEditor.createContextMenuActions.renameAttribute"), am.get("renameAttribute"));
        // (F1) FIX : "Remove" appelle removeAttribute, qui est maintenant corrigé
        addMenuItem(attributeCellPopupMenu,
            LocaleHandler.getString("ContextEditor.createContextMenuActions.removeAttribute"), am.get("removeAttribute"));
        addMenuItem(attributeCellPopupMenu,
            LocaleHandler.getString("ContextEditor.createContextMenuActions.addAttributeLeft"), am.get("addAttributeLeft"));
        addMenuItem(attributeCellPopupMenu,
            LocaleHandler.getString("ContextEditor.createContextMenuActions.addAttributeRight"), am.get("addAttributeRight"));
    }

    private void createMouseActions() {
        MouseAdapter mouseAdapter = new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                int i = matrix.rowAtPoint(e.getPoint());
                int j = matrix.columnAtPoint(e.getPoint());
                int clicks = e.getClickCount();
                if (clicks >= 2 && clicks % 2 == 0 && SwingUtilities.isLeftMouseButton(e)) {
                    if (i > 1 && j > 0) {
                        invokeAction(ContextEditor.this, new ToggleAction(i, j));
                    }
                }
            }
            public void mousePressed(MouseEvent e)  { maybeShowPopup(e); }
            public void mouseReleased(MouseEvent e) { maybeShowPopup(e); }
        };
        matrix.addMouseListener(mouseAdapter);
        matrix.addMouseMotionListener(mouseAdapter);
    }

    private void maybeShowPopup(MouseEvent e) {
        int i = matrix.rowAtPoint(e.getPoint());
        int j = matrix.columnAtPoint(e.getPoint());
        lastActiveRowIndex    = i;
        lastActiveColumnIndex = j;

        if (!e.isPopupTrigger()) return;

        if (i == 0 && j == 0) return;

        if (i > 1 && j > 0) {
            if (matrix.getSelectedColumn() <= 0 || matrix.getSelectedRow() <= 1) {
                matrix.selectCell(i, j);
            }
            cellPopupMenu.show(e.getComponent(), e.getX(), e.getY());
            return;
        }

        if (j == 0 && i > 1) {
            objectCellPopupMenu.show(e.getComponent(), e.getX(), e.getY());
            return;
        }

        // Row 0 = noms des groupes
        if (i == 0 && j > 0) {
            Object val = matrixModel.getValueAt(0, j);
            String groupName = (val != null) ? val.toString().trim() : "";
            if (!groupName.isEmpty()) {
                groupHeaderPopupMenu.show(e.getComponent(), e.getX(), e.getY());
            }
            return;
        }

        // Row 1 = noms des attributs
        if (i == 1 && j > 0) {
            attributeCellPopupMenu.show(e.getComponent(), e.getX(), e.getY());
        }
    }

    /**
     * Helper : retrouver le groupe cliqué à partir de lastActiveColumnIndex.
     */
    private AttributeGroup getClickedGroup() {
        Object val = matrixModel.getValueAt(0, lastActiveColumnIndex);
        String groupName = (val != null) ? val.toString().trim() : "";
        if (groupName.isEmpty()) return null;
        return state.context.getAttributeGroupManager().getGroupByName(groupName);
    }

    /**
     * (F1) Helper : retrouver le nom de l'attribut à la colonne cliquée (row 1).
     * Utilise getAttributeAtVisualColumn pour supporter le mode groupes.
     */
    private String getClickedAttributeName() {
        // getAttributeAtVisualColumn retourne l'attribut réel à la colonne visuelle
        String attr = matrixModel.getAttributeAtVisualColumn(lastActiveColumnIndex);
        if (attr != null) return attr;
        // Fallback : mode sans groupes
        int idx = lastActiveColumnIndex - 1;
        if (idx >= 0 && idx < state.context.getAttributeCount()) {
            return state.context.getAttributeAtIndex(idx);
        }
        return null;
    }

    // =========================================================================
    // Actions
    // =========================================================================

    class CombineActions extends AbstractAction {
        Action first, second;
        CombineActions(Action first, Action second) { this.first = first; this.second = second; }
        public void actionPerformed(ActionEvent e) { invokeAction(e, first); invokeAction(e, second); }
    }

    class ExpandSelectionAction extends AbstractAction {
        int horizontal, vertical;
        ExpandSelectionAction(int horizontal, int vertical) { this.horizontal = horizontal; this.vertical = vertical; }
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) return;
            matrix.saveSelection();
            int lastActiveRowIndexAfter    = clamp(lastActiveRowIndex    + vertical,   2, state.context.getObjectCount() + 1);
            int lastActiveColumnIndexAfter = clamp(lastActiveColumnIndex + horizontal, 1, state.context.getAttributeCount());
            boolean wasRowChange    = (lastActiveRowIndexAfter    - lastActiveRowIndex)    != 0;
            boolean wasColumnChange = (lastActiveColumnIndexAfter - lastActiveColumnIndex) != 0;
            boolean isNewRowInsideOld = lastActiveRowIndexAfter    >= matrix.getLastSelectedRowsStartIndex()    && lastActiveRowIndexAfter    <= matrix.getLastSelectedRowsEndIndex();
            boolean isNewColInsideOld = lastActiveColumnIndexAfter >= matrix.getLastSelectedColumnsStartIndex() && lastActiveColumnIndexAfter <= matrix.getLastSelectedColumnsEndIndex();
            if (wasRowChange) {
                if (isNewRowInsideOld) matrix.removeRowSelectionInterval(lastActiveRowIndex, lastActiveRowIndex);
                else matrix.addRowSelectionInterval(lastActiveRowIndexAfter, lastActiveRowIndexAfter);
                lastActiveRowIndex = lastActiveRowIndexAfter;
            }
            if (wasColumnChange) {
                if (isNewColInsideOld) matrix.removeColumnSelectionInterval(lastActiveColumnIndex, lastActiveColumnIndex);
                else matrix.addColumnSelectionInterval(lastActiveColumnIndexAfter, lastActiveColumnIndexAfter);
                lastActiveColumnIndex = lastActiveColumnIndexAfter;
            }
        }
    }

    class MoveObjectOrAttributeAction extends AbstractAction {
        int horizontal, vertical;
        MoveObjectOrAttributeAction(int horizontal, int vertical) { this.horizontal = horizontal; this.vertical = vertical; }
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) return;
            int rowAfter = clamp(lastActiveRowIndex    + vertical,   1, state.context.getObjectCount());
            int colAfter = clamp(lastActiveColumnIndex + horizontal, 1, state.context.getAttributeCount());
            if ((rowAfter - lastActiveRowIndex) != 0) {
                matrixModel.reorderRows(lastActiveRowIndex, rowAfter);
                matrixModel.fireTableDataChanged();
                lastActiveRowIndex = rowAfter;
                matrix.selectCell(lastActiveRowIndex, lastActiveColumnIndex);
                matrix.saveSelection();
            }
            if ((colAfter - lastActiveColumnIndex) != 0) {
                matrixModel.reorderColumns(lastActiveColumnIndex, colAfter);
                matrixModel.fireTableDataChanged();
                lastActiveColumnIndex = colAfter;
                matrix.selectCell(lastActiveRowIndex, lastActiveColumnIndex);
                matrix.saveSelection();
            }
        }
    }

    class MoveAction extends AbstractAction {
        int horizontal, vertical;
        MoveAction(int horizontal, int vertical) { this.horizontal = horizontal; this.vertical = vertical; }
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) return;
            lastActiveRowIndex    = clamp(lastActiveRowIndex    + vertical,   2, state.context.getObjectCount() + 1);
            lastActiveColumnIndex = clamp(lastActiveColumnIndex + horizontal, 1, state.context.getAttributeCount());
            matrix.selectCell(lastActiveRowIndex, lastActiveColumnIndex);
        }
    }

    class MoveWithCarryAction extends AbstractAction {
        int horizontal, vertical;
        MoveWithCarryAction(int horizontal, int vertical) { this.horizontal = horizontal; this.vertical = vertical; }
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) return;
            if (state.context.getObjectCount() == 0 || state.context.getAttributeCount() == 0) return;
            int i = lastActiveRowIndex + vertical - 1;
            int j = lastActiveColumnIndex + horizontal - 1;
            while (true) {
                if (i < 0) { j--; i = state.context.getObjectCount() - 1; break; }
                if (j < 0) { i--; j = state.context.getAttributeCount() - 1; }
                if (i >= state.context.getObjectCount())    { j++; i = 0; break; }
                if (j >= state.context.getAttributeCount()) { i++; j = 0; }
                break;
            }
            i = mod(i, state.context.getObjectCount());
            j = mod(j, state.context.getAttributeCount());
            lastActiveRowIndex    = i + 1;
            lastActiveColumnIndex = j + 1;
            matrix.selectCell(lastActiveRowIndex, lastActiveColumnIndex);
        }
    }

    class ToggleAction extends AbstractAction {
        int i, j;
        ToggleAction(int i, int j) { this.i = i; this.j = j; }
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) return;
            if (i <= 1 || j <= 0) return;
            int i = clamp(this.i - 2, 0, state.context.getObjectCount() - 1);
            int j = clamp(this.j,     1, state.context.getAttributeCount()) - 1;
            state.saveConf();
            state.context.toggleAttributeForObject(
                state.context.getAttributeAtIndex(j),
                state.context.getObjectAtIndex(i).getIdentifier());
            matrix.saveSelection();
            matrixModel.fireTableDataChanged();
            matrix.restoreSelection();
            state.contextChanged();
            state.getContextEditorUndoManager().makeRedoable();
        }
    }

    class ToggleActiveAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) return;
            invokeAction(ContextEditor.this, new ToggleAction(lastActiveRowIndex, lastActiveColumnIndex));
        }
    }

    class SelectAllAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) return;
            matrix.selectAll(); matrix.saveSelection();
        }
    }

    class SelectNoneAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) return;
            matrix.clearSelection(); matrix.saveSelection();
        }
    }

    abstract class AbstractFillClearInvertAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) return;
            int i1 = matrix.getSelectedRow()    - 1;
            int i2 = i1 + matrix.getSelectedRowCount();
            int j1 = matrix.getSelectedColumn() - 1;
            int j2 = j1 + matrix.getSelectedColumnCount();
            matrix.saveSelection();
            state.saveConf();
            execute(i1, i2, j1, j2);
            matrixModel.fireTableDataChanged();
            matrix.restoreSelection();
            state.contextChanged();
            state.getContextEditorUndoManager().makeRedoable();
        }
        abstract void execute(int i1, int i2, int j1, int j2);
    }

    class FillAction   extends AbstractFillClearInvertAction { void execute(int i1, int i2, int j1, int j2) { state.context.fill(i1, i2, j1, j2); } }
    class ClearAction  extends AbstractFillClearInvertAction { void execute(int i1, int i2, int j1, int j2) { state.context.clear(i1, i2, j1, j2); } }
    class InvertAction extends AbstractFillClearInvertAction { void execute(int i1, int i2, int j1, int j2) { state.context.invert(i1, i2, j1, j2); } }

    class ClarifyObjectsAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) return;
            Long id = state.getStatusBar().startCalculation();
            ClarificationReductionWorker crw = new ClarificationReductionWorker(state, matrix, id, true, false, false);
            crw.addPropertyChangeListener(new StatusBarPropertyChangeListener(id, state.getStatusBar()));
            state.getStatusBar().addCalculation(id, crw); crw.execute();
        }
    }
    class ClarifyAttributesAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) return;
            Long id = state.getStatusBar().startCalculation();
            ClarificationReductionWorker crw = new ClarificationReductionWorker(state, matrix, id, false, false, false);
            crw.addPropertyChangeListener(new StatusBarPropertyChangeListener(id, state.getStatusBar()));
            state.getStatusBar().addCalculation(id, crw); crw.execute();
        }
    }
    class ReduceObjectsAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) return;
            Long id = state.getStatusBar().startCalculation();
            ClarificationReductionWorker crw = new ClarificationReductionWorker(state, matrix, id, true, true, false);
            crw.addPropertyChangeListener(new StatusBarPropertyChangeListener(id, state.getStatusBar()));
            state.getStatusBar().addCalculation(id, crw); crw.execute();
        }
    }
    class ReduceAttributesAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) return;
            Long id = state.getStatusBar().startCalculation();
            ClarificationReductionWorker crw = new ClarificationReductionWorker(state, matrix, id, false, true, false);
            crw.addPropertyChangeListener(new StatusBarPropertyChangeListener(id, state.getStatusBar()));
            state.getStatusBar().addCalculation(id, crw); crw.execute();
        }
    }
    class ReduceAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) return;
            Long id = state.getStatusBar().startCalculation();
            ClarificationReductionWorker crw = new ClarificationReductionWorker(state, matrix, id, false, false, true);
            crw.addPropertyChangeListener(new StatusBarPropertyChangeListener(id, state.getStatusBar()));
            state.getStatusBar().addCalculation(id, crw); crw.execute();
        }
    }

    class TransposeAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) return;
            state.saveConf();
            state.context.transpose();
            matrixModel.fireTableStructureChanged();
            matrix.clearSelection(); matrix.saveSelection();
            state.contextChanged();
            state.getContextEditorUndoManager().makeRedoable();
        }
    }

    class AddAttributeAtAction extends AbstractAction {
        int index;
        AddAttributeAtAction(int index) { this.index = index; }
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) return;
            state.saveConf(); matrix.saveSelection();
            addAttributeAt(index);
            matrix.restoreSelection(); state.contextChanged();
            state.getContextEditorUndoManager().makeRedoable();
        }
    }

    class AddObjectAtAction extends AbstractAction {
        int index;
        AddObjectAtAction(int index) { this.index = index; }
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) return;
            state.saveConf(); matrix.saveSelection();
            addObjectAt(index);
            matrix.restoreSelection(); state.contextChanged();
            state.getContextEditorUndoManager().makeRedoable();
        }
    }

    class AddAttributeAfterActiveAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) return;
            invokeAction(ContextEditor.this, new AddAttributeAtAction(lastActiveColumnIndex));
            lastActiveColumnIndex++;
            matrix.selectCell(lastActiveRowIndex, lastActiveColumnIndex);
            matrix.saveSelection();
        }
    }
    class AddAttributeBeforeActiveAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) return;
            invokeAction(ContextEditor.this, new AddAttributeAtAction(lastActiveColumnIndex - 1));
        }
    }
    class AddAttributeAtEndAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            boolean old = matrix.isRenaming; matrix.isRenaming = false;
            invokeAction(ContextEditor.this, new AddAttributeAtAction(state.context.getAttributeCount()));
            matrix.isRenaming = old;
        }
    }
    class AddObjectAfterActiveAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) return;
            invokeAction(ContextEditor.this, new AddObjectAtAction(lastActiveRowIndex));
            lastActiveRowIndex++;
            matrix.selectCell(lastActiveRowIndex, lastActiveColumnIndex);
            matrix.saveSelection();
        }
    }
    class AddObjectBeforeActiveAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) return;
            invokeAction(ContextEditor.this, new AddObjectAtAction(lastActiveRowIndex - 1));
        }
    }
    class AddObjectAtEndAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            boolean old = matrix.isRenaming; matrix.isRenaming = false;
            invokeAction(ContextEditor.this, new AddObjectAtAction(state.context.getObjectCount()));
            matrix.isRenaming = old;
        }
    }

    class RenameActiveObjectAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming || state.context.getObjectCount() == 0) return;
            final String name = state.context.getObjectAtIndex(lastActiveRowIndex - 2).getIdentifier();
            final JTextField t = matrix.renameRowHeader(lastActiveRowIndex);
            Timer timer = new Timer(0, new ActionListener() {
                public void actionPerformed(ActionEvent e) { t.setText(name); t.selectAll(); }
            });
            timer.setRepeats(false); timer.setInitialDelay(10); timer.start();
        }
    }

    class RenameActiveAttributeAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming || state.context.getObjectCount() == 0) return;
            // (F1) FIX : utiliser getClickedAttributeName() pour le bon attribut en mode groupes
            final String name = getClickedAttributeName();
            if (name == null) return;
            final JTextField t = matrix.renameColumnHeader(lastActiveColumnIndex);
            Timer timer = new Timer(0, new ActionListener() {
                public void actionPerformed(ActionEvent e) { t.setText(name); t.selectAll(); }
            });
            timer.setRepeats(false); timer.setInitialDelay(10); timer.start();
        }
    }

    class RemoveActiveObjectAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming || state.context.getObjectCount() == 0) return;
            matrix.saveSelection();
            try {
                state.saveConf();
                state.context.removeObject(state.context.getObjectAtIndex(lastActiveRowIndex - 2).getIdentifier());
                if (lastActiveRowIndex - 1 >= state.context.getObjectCount()) lastActiveRowIndex--;
            } catch (IllegalObjectException e1) { e1.printStackTrace(); }
            matrixModel.fireTableStructureChanged();
            matrix.invalidate(); matrix.repaint(); matrix.restoreSelection();
            state.contextChanged(); state.getContextEditorUndoManager().makeRedoable();
        }
    }

    /**
     * (F1) FIX : Utilise getClickedAttributeName() pour trouver le bon attribut
     * même en mode groupes (où l'ordre visuel ≠ ordre interne).
     */
    class RemoveActiveAttributeAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming || state.context.getAttributeCount() == 0) return;

            // FIX : récupérer l'attribut réel à la colonne visuelle cliquée
            String attrName = getClickedAttributeName();
            if (attrName == null) return;

            matrix.saveSelection();
            state.saveConf();

            // Retirer l'attribut de son groupe éventuel
            AttributeGroup group = state.context.getGroupForAttribute(attrName);
            if (group != null) {
                state.context.getAttributeGroupManager()
                    .removeAttributeFromGroup(group.getGroupId(), attrName);
            }

            // Supprimer l'attribut du contexte
            state.context.removeAttribute(attrName);
            matrix.updateColumnWidths(lastActiveColumnIndex);
            if (lastActiveColumnIndex - 1 >= state.context.getAttributeCount()) lastActiveColumnIndex--;

            matrixModel.fireTableStructureChanged();
            matrix.invalidate(); matrix.repaint(); matrix.restoreSelection();
            state.contextChanged(); state.getContextEditorUndoManager().makeRedoable();
        }
    }

    class RemoveSelectedObjectsAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming || state.context.getAttributeCount() == 0) return;
            matrix.saveSelection();
            int i = Math.min(matrix.getLastSelectedRowsStartIndex(), matrix.getLastSelectedRowsEndIndex()) - 2;
            int d = Math.abs(matrix.getLastSelectedRowsStartIndex() - matrix.getLastSelectedRowsEndIndex()) + 1;
            state.saveConf();
            for (int unused = 0; unused < d; unused++) {
                try { state.context.removeObject(state.context.getObjectAtIndex(i).getIdentifier()); }
                catch (IllegalObjectException e1) { e1.printStackTrace(); }
            }
            matrixModel.fireTableStructureChanged();
            matrix.invalidate(); matrix.repaint();
            state.contextChanged(); state.getContextEditorUndoManager().makeRedoable();
        }
    }

    class RemoveSelectedAttributesAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming || state.context.getAttributeCount() == 0) return;
            matrix.saveSelection();
            int i = Math.min(matrix.getLastSelectedColumnsStartIndex(), matrix.getLastSelectedColumnsEndIndex()) - 1;
            int d = Math.abs(matrix.getLastSelectedColumnsStartIndex() - matrix.getLastSelectedColumnsEndIndex()) + 1;
            state.saveConf();
            for (int unused = 0; unused < d; unused++) {
                state.context.removeAttribute(state.context.getAttributeAtIndex(i));
                matrix.updateColumnWidths(i + 1);
            }
            matrixModel.fireTableStructureChanged();
            matrix.invalidate(); matrix.repaint();
            state.contextChanged(); state.getContextEditorUndoManager().makeRedoable();
        }
    }

    class CompactAction extends AbstractAction implements ItemListener {
        public void itemStateChanged(ItemEvent e) {
            if (e.getStateChange() == ItemEvent.SELECTED) { state.guiConf.compactMatrix = true;  matrix.compact(); }
            else                                           { state.guiConf.compactMatrix = false; matrix.uncompact(); }
        }
        public void actionPerformed(ActionEvent e) {}
    }

    // =========================================================================
    // (F1) Actions groupes
    // =========================================================================

    class GroupSelectedAttributesAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            java.util.Collection<String> availableAttrs = state.context.getUngroupedAttributes();
            if (availableAttrs.isEmpty()) {
                JOptionPane.showMessageDialog(ContextEditor.this, "All attributes are already grouped!",
                    "No Attributes Available", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            AttributeSelectionDialog dialog = new AttributeSelectionDialog(
                ContextEditor.this, "Select attributes to group:",
                new java.util.ArrayList<String>(availableAttrs));
            dialog.setVisible(true);
            if (dialog.isConfirmed()) {
                java.util.Set<String> selectedAttrs = dialog.getSelectedAttributes();
                if (selectedAttrs.isEmpty()) {
                    JOptionPane.showMessageDialog(ContextEditor.this, "Please select at least one attribute!",
                        "No Selection", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                String groupName = JOptionPane.showInputDialog(ContextEditor.this, "Enter group name:",
                    "Create Group", JOptionPane.QUESTION_MESSAGE);
                if (groupName == null || groupName.isEmpty()) return;
                String groupNameUpper = groupName.trim().toUpperCase();
                if (state.context.getAttributeGroupManager().groupNameExists(groupNameUpper)) {
                    JOptionPane.showMessageDialog(ContextEditor.this,
                        "A group with name '" + groupNameUpper + "' already exists!",
                        "Duplicate Group Name", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                state.saveConf();
                String groupId = state.context.createAttributeGroup(groupNameUpper, selectedAttrs);
                if (groupId != null) {
                    state.context.reorganizeAttributesForGroups();
                    matrixModel.fireTableStructureChanged();
                    matrix.invalidate(); matrix.repaint();
                    state.contextChanged(); state.getContextEditorUndoManager().makeRedoable();
                    JOptionPane.showMessageDialog(ContextEditor.this,
                        "Group '" + groupNameUpper + "' created with " + selectedAttrs.size() + " attributes!",
                        "Success", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        }
    }

    class AddAttributesToGroupAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) return;
            AttributeGroup group = getClickedGroup();
            if (group == null) {
                JOptionPane.showMessageDialog(ContextEditor.this,
                    "No group found at this position.", "No Group", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            java.util.Collection<String> ungrouped = state.context.getUngroupedAttributes();
            if (ungrouped.isEmpty()) {
                JOptionPane.showMessageDialog(ContextEditor.this,
                    "All attributes are already in a group.\nThere are no free attributes to add.",
                    "No Free Attributes", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            AttributeSelectionDialog dialog = new AttributeSelectionDialog(
                ContextEditor.this,
                "Select attributes to add to group '" + group.getGroupName() + "':",
                new java.util.ArrayList<String>(ungrouped));
            dialog.setVisible(true);
            if (!dialog.isConfirmed()) return;
            java.util.Set<String> selected = dialog.getSelectedAttributes();
            if (selected.isEmpty()) {
                JOptionPane.showMessageDialog(ContextEditor.this,
                    "No attributes selected.", "No Selection", JOptionPane.WARNING_MESSAGE);
                return;
            }
            state.saveConf();
            for (String attr : selected) {
                state.context.getAttributeGroupManager().addAttributeToGroup(group.getGroupId(), attr);
            }
            state.context.reorganizeAttributesForGroups();
            matrixModel.fireTableStructureChanged();
            matrix.invalidate(); matrix.repaint();
            state.contextChanged(); state.getContextEditorUndoManager().makeRedoable();
            JOptionPane.showMessageDialog(ContextEditor.this,
                selected.size() + " attribute(s) added to group '" + group.getGroupName() + "'.",
                "Success", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * (F1) NOUVEAU : Supprimer des attributs sélectionnés D'UN GROUPE
     * (suppression définitive du contexte, pas juste un détachement).
     */
    class RemoveAttributesFromGroupAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) return;
            AttributeGroup group = getClickedGroup();
            if (group == null) {
                JOptionPane.showMessageDialog(ContextEditor.this,
                    "No group found at this position.", "No Group", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            java.util.List<String> groupAttrs = new java.util.ArrayList<String>(group.getAttributeNames());
            if (groupAttrs.isEmpty()) {
                JOptionPane.showMessageDialog(ContextEditor.this,
                    "Group '" + group.getGroupName() + "' has no attributes.",
                    "Empty Group", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            // Afficher les checkboxes avec les attributs du groupe
            AttributeSelectionDialog dialog = new AttributeSelectionDialog(
                ContextEditor.this,
                "Select attributes to permanently DELETE from group '" + group.getGroupName() + "':",
                groupAttrs);
            dialog.setVisible(true);
            if (!dialog.isConfirmed()) return;
            java.util.Set<String> toDelete = dialog.getSelectedAttributes();
            if (toDelete.isEmpty()) {
                JOptionPane.showMessageDialog(ContextEditor.this,
                    "No attributes selected.", "No Selection", JOptionPane.WARNING_MESSAGE);
                return;
            }
            // Confirmation
            int confirm = JOptionPane.showConfirmDialog(ContextEditor.this,
                "Detach " + toDelete.size() + " attribute(s) from group '"
                + group.getGroupName() + "':\n" + toDelete.toString()
                + "\n\nThe attributes will remain in the context (ungrouped).",
                "Confirm Remove from Group",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) return;
            state.saveConf();
            // Détacher les attributs du groupe SEULEMENT (ils restent dans le contexte)
            for (String attr : toDelete) {
                state.context.getAttributeGroupManager()
                    .removeAttributeFromGroup(group.getGroupId(), attr);
            }
            matrixModel.fireTableStructureChanged();
            matrix.invalidate(); matrix.repaint();
            state.contextChanged(); state.getContextEditorUndoManager().makeRedoable();
        }
    }

    class UngroupAttributeAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) return;
            AttributeGroup group = getClickedGroup();
            if (group == null) {
                JOptionPane.showMessageDialog(ContextEditor.this,
                    "No group found at this position.", "No Group", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            int confirm = JOptionPane.showConfirmDialog(ContextEditor.this,
                "Detach all attributes from group '" + group.getGroupName() + "'?\n" +
                "The attributes will be kept in the context.",
                "Confirm Ungroup", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;
            state.saveConf();
            state.context.removeAttributeGroup(group.getGroupId());
            matrixModel.fireTableStructureChanged();
            matrix.invalidate(); matrix.repaint();
            state.contextChanged(); state.getContextEditorUndoManager().makeRedoable();
        }
    }

    class DeleteGroupWithAttributesAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) return;
            AttributeGroup group = getClickedGroup();
            if (group == null) {
                JOptionPane.showMessageDialog(ContextEditor.this,
                    "No group found at this position.", "No Group", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            java.util.List<String> attrsToDelete = new java.util.ArrayList<String>(group.getAttributeNames());
            int confirm = JOptionPane.showConfirmDialog(ContextEditor.this,
                "Delete group '" + group.getGroupName() + "' and its " + attrsToDelete.size() + " attribute(s):\n" +
                attrsToDelete.toString() + "\n\nWARNING: Attributes will be permanently removed from ALL objects!",
                "Delete Group + Attributes", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) return;
            state.saveConf();
            for (String attr : attrsToDelete) { state.context.removeAttribute(attr); }
            state.context.removeAttributeGroup(group.getGroupId());
            matrixModel.fireTableStructureChanged();
            matrix.invalidate(); matrix.repaint();
            state.contextChanged(); state.getContextEditorUndoManager().makeRedoable();
        }
    }

    class RenameGroupAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) return;
            AttributeGroup group = getClickedGroup();
            if (group == null) return;
            String newName = JOptionPane.showInputDialog(ContextEditor.this, "Enter new group name:",
                group.getGroupName(), JOptionPane.QUESTION_MESSAGE);
            if (newName != null && !newName.isEmpty()) {
                state.saveConf();
                state.context.getAttributeGroupManager().renameGroup(group.getGroupId(), newName);
                matrixModel.fireTableStructureChanged();
                matrix.invalidate(); matrix.repaint();
                state.contextChanged(); state.getContextEditorUndoManager().makeRedoable();
            }
        }
    }

    class ToggleGroupExpansionAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) return;
            AttributeGroup group = getClickedGroup();
            if (group == null) return;
            matrixModel.toggleGroupExpansion(group.getGroupId());
            matrix.invalidate(); matrix.repaint();
        }
    }

    class GenerateLatticeForGroupAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) return;
            java.util.List<AttributeGroup> allGroups = new java.util.ArrayList<AttributeGroup>(
                state.context.getAllAttributeGroups());
            java.util.Collection<String> ungroupedAttrs = state.context.getUngroupedAttributes();
            java.util.List<String> options = new java.util.ArrayList<String>();
            java.util.Map<String, AttributeGroup> groupMap = new java.util.HashMap<String, AttributeGroup>();
            for (AttributeGroup g : allGroups) {
                String label = g.getGroupName() + " (" + g.getAttributeCount() + " attributes)";
                options.add(label); groupMap.put(label, g);
            }
            for (String attr : ungroupedAttrs) {
                options.add(attr + " (single attribute)");
                groupMap.put(attr + " (single attribute)", null);
            }
            if (options.isEmpty()) {
                JOptionPane.showMessageDialog(ContextEditor.this, "No groups or attributes available.",
                    "Empty Context", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            String[] optionsArray = options.toArray(new String[0]);
            String selected = (String) JOptionPane.showInputDialog(ContextEditor.this,
                "Select a group to generate lattice for:", "Generate Lattice for Group",
                JOptionPane.QUESTION_MESSAGE, null, optionsArray, optionsArray[0]);
            if (selected == null) return;
            AttributeGroup selectedGroup = groupMap.get(selected);
            if (selectedGroup == null) { state.setCurrentGroupIdForLattice(null); state.contextChanged(); return; }
            state.setCurrentGroupIdForLattice(selectedGroup.getGroupId());
            state.contextChanged();
            JOptionPane.showMessageDialog(ContextEditor.this,
                "Lattice generated for group: " + selectedGroup.getGroupName(),
                "Lattice Generated", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void addAttributeAt(final int i) {
        String name = "attr" + i;
        while (state.context.existsAttributeAlready(name)) name = name + "'";
        state.context.addAttributeAt(name, i);
        matrixModel.fireTableStructureChanged();
        SwingUtilities.invokeLater(new Runnable() {
            public void run() { matrix.renameColumnHeader(i + 1); }
        });
    }

    private void addObjectAt(final int i) {
        String name = "obj" + i;
        while (state.context.existsObjectAlready(name)) name = name + "'";
        FullObject<String, String> newObject = new FullObject<>(name);
        state.context.addObjectAt(newObject, i);
        matrixModel.fireTableStructureChanged();
        SwingUtilities.invokeLater(new Runnable() {
            public void run() { matrix.renameRowHeader(i + 1); }
        });
    }

    @SuppressWarnings("serial")
    private class AttributeSelectionDialog extends JDialog {
        private boolean confirmed = false;
        private java.util.Set<String> selectedAttributes = new java.util.HashSet<String>();
        private java.util.List<JCheckBox> checkboxes     = new java.util.ArrayList<JCheckBox>();

        public AttributeSelectionDialog(ContextEditor contextEditor, String labelText,
                java.util.List<String> availableAttributes) {
            super(SwingUtilities.getWindowAncestor(contextEditor),
                "Select Attributes", JDialog.ModalityType.APPLICATION_MODAL);
            setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
            mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
            mainPanel.add(new JLabel(labelText), BorderLayout.NORTH);
            JPanel checkboxPanel = new JPanel();
            checkboxPanel.setLayout(new BoxLayout(checkboxPanel, BoxLayout.Y_AXIS));
            for (String attr : availableAttributes) {
                JCheckBox cb = new JCheckBox(attr);
                checkboxes.add(cb); checkboxPanel.add(cb);
            }
            JScrollPane sp = new JScrollPane(checkboxPanel);
            sp.setPreferredSize(new Dimension(320, 200));
            mainPanel.add(sp, BorderLayout.CENTER);
            JPanel buttonPanel = new JPanel();
            JButton ok = new JButton("OK");
            JButton cancel = new JButton("Cancel");
            ok.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    confirmed = true;
                    for (JCheckBox cb : checkboxes) if (cb.isSelected()) selectedAttributes.add(cb.getText());
                    dispose();
                }
            });
            cancel.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) { confirmed = false; dispose(); }
            });
            buttonPanel.add(ok); buttonPanel.add(cancel);
            mainPanel.add(buttonPanel, BorderLayout.SOUTH);
            add(mainPanel); pack();
            setLocationRelativeTo(contextEditor);
        }

        public boolean isConfirmed()                         { return confirmed; }
        public java.util.Set<String> getSelectedAttributes() { return selectedAttributes; }
    }
}