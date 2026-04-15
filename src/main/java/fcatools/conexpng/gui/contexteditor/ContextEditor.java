package fcatools.conexpng.gui.contexteditor;

import static fcatools.conexpng.Util.addMenuItem;
import static fcatools.conexpng.Util.clamp;
import static fcatools.conexpng.Util.createButton;
import static fcatools.conexpng.Util.createToggleButton;
import static fcatools.conexpng.Util.invokeAction;
import static fcatools.conexpng.Util.mod;
import static javax.swing.KeyStroke.getKeyStroke;

import java.awt.BorderLayout;
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
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
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

// (F1) Imports pour Fonctionnalité 1 : Groupes d'Attributs
import fcatools.conexpng.model.AttributeGroup;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JCheckBox;
import javax.swing.JScrollPane;
import javax.swing.BorderFactory;
import javax.swing.border.EmptyBorder;
import javax.swing.BoxLayout;
import java.awt.Dimension;
import java.awt.BorderLayout;

/**
 * The class responsible for displaying and interacting with ConExpNG's context
 * editor. The main component of this view is a customised JTable, that is more
 * akin to a spreadsheet editor, serving as our context editor.
 * 
 * Notes:
 * 
 * Generally, the code between ContextEditor and ContextMatrix is divided as per
 * the following guidelines:
 * 
 * - More general code is in ContextMatrix. - Code that also pertains to other
 * parts of the context editor (e.g. toolbar) other than the matrix is in
 * ContextEditor. - Code that needs to know about the MatrixModel specifically
 * and not only about AbstractTableModel is in ContextEditor as ContextMatrix
 * should not be coupled with a concrete model in order to have a seperation
 * between model and view.
 * 
 * E.g. PopupMenu code is in ContextEditor (and not in ContextMatrix) as for a
 * different Model one would probably use different PopupMenus, that means the
 * PopupMenus are coupled with MatrixModel.
 */
@SuppressWarnings("serial")
public class ContextEditor extends View {

    private static final long serialVersionUID = 1660117627650529212L;

    // Choose correct modifier key (STRG or CMD) based on platform
    private static final int MASK = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

    // Our JTable customisation and its respective data model
    private final ContextMatrix matrix;
    private final ContextMatrixModel matrixModel;
    // Context menus
    final WebPopupMenu cellPopupMenu;
    final WebPopupMenu objectCellPopupMenu;
    final WebPopupMenu attributeCellPopupMenu;
    
    // (F1) NOUVEAU : Menu contextuel pour les groupes d'attributs
    final WebPopupMenu groupHeaderPopupMenu;

    WebToggleButton compactMatrixButton, showArrowRelationsButton;

    // For remembering which header cell has been right-clicked
    // For movement inside the matrix
    // Due to unfortunate implications of our JTable customisation we need to
    // rely on this "hack"
    int lastActiveRowIndex;
    int lastActiveColumnIndex;

    public ContextEditor(final Conf state) {
        super(state);
        setLayout(new BorderLayout());

        // Initialize various components
        cellPopupMenu = new WebPopupMenu();
        objectCellPopupMenu = new WebPopupMenu();
        attributeCellPopupMenu = new WebPopupMenu();
        // (F1) Initialiser le menu contextuel des groupes
        groupHeaderPopupMenu = new WebPopupMenu();
        
        matrixModel = new ContextMatrixModel(state);
        matrix = new ContextMatrix(matrixModel, state.guiConf.columnWidths);
        JScrollPane scrollPane = matrix.createStripedJScrollPane(getBackground());
        scrollPane.setBorder(new EmptyBorder(3, 3, 3, 3));
        toolbar.setFloatable(false);
        panel.setLayout(new BorderLayout());
        panel.add(toolbar, BorderLayout.WEST);
        panel.add(scrollPane, BorderLayout.CENTER);
        add(panel);
        // Make context editor tighter
        setBorder(new EmptyBorder(3, -3, -4, -3));
        toolbar.setRound(4);

        // Add actions
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

        // Force an update of the table to display it correctly
        matrixModel.fireTableStructureChanged();
    }

    // If context is not changed through the context editor (e.g. by
    // exploration) be sure to reflect these changes inside the matrix
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

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Behaviour Initialization
    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void registerActions() {
        ActionMap am = matrix.getActionMap();

        am.put("up", new MoveAction(0, -1));
        am.put("down", new MoveAction(0, +1));
        am.put("left", new MoveAction(-1, 0));
        am.put("right", new MoveAction(+1, 0));
        am.put("upCarry", new MoveWithCarryAction(0, -1));
        am.put("downCarry", new MoveWithCarryAction(0, +1));
        am.put("leftCarry", new MoveWithCarryAction(-1, 0));
        am.put("rightCarry", new MoveWithCarryAction(+1, 0));
        am.put("gotoFirstObject", new MoveAction(0, -10000));
        am.put("gotoLastObject", new MoveAction(0, +10000));
        am.put("gotoFirstAttribute", new MoveAction(-10000, 0));
        am.put("gotoLastAttribute", new MoveAction(+10000, 0));
        am.put("moveObjectUp", new MoveObjectOrAttributeAction(0, -1));
        am.put("moveObjectDown", new MoveObjectOrAttributeAction(0, +1));
        am.put("moveAttributeLeft", new MoveObjectOrAttributeAction(-1, 0));
        am.put("moveAttributeRight", new MoveObjectOrAttributeAction(+1, 0));

        am.put("selectAll", new SelectAllAction());
        am.put("selectNone", new SelectNoneAction());
        am.put("selectUp", new ExpandSelectionAction(0, -1));
        am.put("selectDown", new ExpandSelectionAction(0, +1));
        am.put("selectLeft", new ExpandSelectionAction(-1, 0));
        am.put("selectRight", new ExpandSelectionAction(+1, 0));

        am.put("renameObject", new RenameActiveObjectAction());
        am.put("renameAttribute", new RenameActiveAttributeAction());
        am.put("removeObject", new RemoveActiveObjectAction());
        am.put("removeSelectedObjects", new RemoveSelectedObjectsAction());
        am.put("removeAttribute", new RemoveActiveAttributeAction());
        am.put("removeSelectedAttributes", new RemoveSelectedAttributesAction());
        am.put("addObjectBelow", new AddObjectAfterActiveAction());
        am.put("addObjectAbove", new AddObjectBeforeActiveAction());
        am.put("addObjectAtEnd", new AddObjectAtEndAction());
        am.put("addAttributeRight", new AddAttributeAfterActiveAction());
        am.put("addAttributeLeft", new AddAttributeBeforeActiveAction());
        am.put("addAttributeAtEnd", new AddAttributeAtEndAction());

        am.put("toggle", new ToggleActiveAction());
        am.put("invert", new InvertAction());
        am.put("fill", new FillAction());
        am.put("clear", new ClearAction());

        am.put("clarifyObjects", new ClarifyObjectsAction());
        am.put("clarifyAttributes", new ClarifyAttributesAction());
        am.put("reduceObjects", new ReduceObjectsAction());
        am.put("reduceAttributes", new ReduceAttributesAction());
        am.put("reduce", new ReduceAction());
        am.put("transpose", new TransposeAction());
        am.put("compact", new CompactAction());

        // (F1) NOUVELLES ACTIONS pour Fonctionnalité 1 : Groupes d'Attributs
        am.put("groupSelectedAttributes", new GroupSelectedAttributesAction());
        am.put("ungroupAttribute", new UngroupAttributeAction());
        am.put("renameGroup", new RenameGroupAction());
        am.put("generateLatticeForGroup", new GenerateLatticeForGroupAction());
        am.put("toggleGroupExpansion", new ToggleGroupExpansionAction());
    }

    private void createKeyActions() {
        InputMap im = matrix.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        im.put(getKeyStroke(KeyEvent.VK_UP, 0), "up");
        im.put(getKeyStroke(KeyEvent.VK_DOWN, 0), "down");
        im.put(getKeyStroke(KeyEvent.VK_LEFT, 0), "left");
        im.put(getKeyStroke(KeyEvent.VK_RIGHT, 0), "right");
        im.put(getKeyStroke(KeyEvent.VK_K, 0), "upCarry");
        im.put(getKeyStroke(KeyEvent.VK_J, 0), "downCarry");
        im.put(getKeyStroke(KeyEvent.VK_H, 0), "leftCarry");
        im.put(getKeyStroke(KeyEvent.VK_L, 0), "rightCarry");
        im.put(getKeyStroke(KeyEvent.VK_G, 0), "gotoFirstObject");
        im.put(getKeyStroke(KeyEvent.VK_G, KeyEvent.SHIFT_MASK), "gotoLastObject");
        im.put(getKeyStroke(KeyEvent.VK_0, 0), "gotoFirstAttribute");
        im.put(getKeyStroke(KeyEvent.VK_DOLLAR, 0), "gotoLastAttribute");
        im.put(getKeyStroke(KeyEvent.VK_UP, MASK), "moveObjectUp");
        im.put(getKeyStroke(KeyEvent.VK_DOWN, MASK), "moveObjectDown");
        im.put(getKeyStroke(KeyEvent.VK_LEFT, MASK), "moveAttributeLeft");
        im.put(getKeyStroke(KeyEvent.VK_RIGHT, MASK), "moveAttributeRight");
        im.put(getKeyStroke(KeyEvent.VK_K, MASK), "moveObjectUp");
        im.put(getKeyStroke(KeyEvent.VK_J, MASK), "moveObjectDown");
        im.put(getKeyStroke(KeyEvent.VK_H, MASK), "moveAttributeLeft");
        im.put(getKeyStroke(KeyEvent.VK_L, MASK), "moveAttributeRight");

        im.put(getKeyStroke(KeyEvent.VK_A, MASK), "selectAll");
        im.put(getKeyStroke(KeyEvent.VK_ESCAPE, 0), "selectNone");
        im.put(getKeyStroke(KeyEvent.VK_UP, KeyEvent.SHIFT_MASK), "selectUp");
        im.put(getKeyStroke(KeyEvent.VK_DOWN, KeyEvent.SHIFT_MASK), "selectDown");
        im.put(getKeyStroke(KeyEvent.VK_LEFT, KeyEvent.SHIFT_MASK), "selectLeft");
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
        group = new WebButtonGroup(WebButtonGroup.VERTICAL, true, createButton(
                LocaleHandler.getString("ContextEditor.createButtonActions.addObject"), "addObject",
                "icons/context editor/add_object.png", am.get("addObjectAtEnd")), createButton(
                LocaleHandler.getString("ContextEditor.createButtonActions.clarifyObjects"), "clarifyObjects",
                "icons/context editor/clarify_objects.png", am.get("clarifyObjects")), createButton(
                LocaleHandler.getString("ContextEditor.createButtonActions.reduceObjects"), "reduceObjects",
                "icons/context editor/reduce_objects.png", am.get("reduceObjects")));
        group.setButtonsDrawFocus(false);
        toolbar.add(group);
        toolbar.addSeparator();
        group = new WebButtonGroup(WebButtonGroup.VERTICAL, true, createButton(
                LocaleHandler.getString("ContextEditor.createButtonActions.addAttribute"), "addAttribute",
                "icons/context editor/add_attribute.png", am.get("addAttributeAtEnd")), createButton(
                LocaleHandler.getString("ContextEditor.createButtonActions.clarifyAttributes"), "clarifyAttributes",
                "icons/context editor/clarify_attributes.png", am.get("clarifyAttributes")), createButton(
                LocaleHandler.getString("ContextEditor.createButtonActions.reduceAttributes"), "reduceAttributes",
                "icons/context editor/reduce_attributes.png", am.get("reduceAttributes")));
        group.setButtonsDrawFocus(false);
        toolbar.add(group);
        toolbar.addSeparator();
        group = new WebButtonGroup(WebButtonGroup.VERTICAL, true, createButton(
                LocaleHandler.getString("ContextEditor.createButtonActions.reduceContext"), "reduceContext",
                "icons/context editor/reduce_context.png", am.get("reduce")), createButton(
                LocaleHandler.getString("ContextEditor.createButtonActions.transposeContext"), "transposeContext",
                "icons/context editor/transpose.png", am.get("transpose")));
        group.setButtonsDrawFocus(false);
        toolbar.add(group);
        toolbar.addSeparator();
        compactMatrixButton = createToggleButton(
                LocaleHandler.getString("ContextEditor.createButtonActions.compactMatrix"), "compactMatrix",
                "icons/context editor/compact.png", (ItemListener) am.get("compact"));
        showArrowRelationsButton = createToggleButton(
                LocaleHandler.getString("ContextEditor.createButtonActions.showArrowRelations"), "showArrowRelations",
                "icons/context editor/show_arrow_relations.png", null);
        updateButtonSelection();
        group = new WebButtonGroup(WebButtonGroup.VERTICAL, false, compactMatrixButton, showArrowRelationsButton);
        group.setButtonsDrawFocus(false);
        toolbar.add(group);

        // (F1) GROUPE DE BOUTONS pour Fonctionnalité 1 : Groupes d'Attributs
        toolbar.addSeparator();
        group = new WebButtonGroup(WebButtonGroup.VERTICAL, true, 
            createButton(
                "Group Attributes", "groupAttributes",
                "icons/context editor/reduce_context.png",
                am.get("groupSelectedAttributes")),
            createButton(
                "Ungroup", "ungroup",
                "icons/context editor/transpose.png",
                am.get("ungroupAttribute")),
            createButton(
                "Lattice for Group", "latticeForGroup",
                "icons/context editor/add_attribute.png",
                am.get("generateLatticeForGroup")));
        group.setButtonsDrawFocus(false);
        toolbar.add(group);
    }

    /**
     * Updates the toggle button selection of compactMatrixButton and
     * showArrowRelationsButton to reflect the GUIConf state.
     */
    public void updateButtonSelection() {
        if (state.guiConf.compactMatrix) {
            compactMatrixButton.setSelected(true);
            matrix.compact();
        } else {
            compactMatrixButton.setSelected(false);
            matrix.uncompact();
        }
        if (state.guiConf.showArrowRelations) {
            showArrowRelationsButton.setSelected(true);
        } else {
            showArrowRelationsButton.setSelected(false);
        }
    }

    private void createContextMenuActions() {
        ActionMap am = matrix.getActionMap();
        
        // (F1) NOUVEAU : Menu contextuel pour les groupes d'attributs
        addMenuItem(groupHeaderPopupMenu,
                "Rename Group", am.get("renameGroup"));
        addMenuItem(groupHeaderPopupMenu,
                "Generate Lattice for Group", am.get("generateLatticeForGroup"));
        groupHeaderPopupMenu.add(new WebPopupMenu.Separator());
        addMenuItem(groupHeaderPopupMenu,
                "Ungroup", am.get("ungroupAttribute"));
        
        // Menus existants
        addMenuItem(cellPopupMenu, LocaleHandler.getString("ContextEditor.createContextMenuActions.selectAll"),
                am.get("selectAll"));
        cellPopupMenu.add(new WebPopupMenu.Separator());
        addMenuItem(cellPopupMenu, LocaleHandler.getString("ContextEditor.createContextMenuActions.fill"),
                am.get("fill"));
        addMenuItem(cellPopupMenu, LocaleHandler.getString("ContextEditor.createContextMenuActions.clear"),
                am.get("clear"));
        addMenuItem(cellPopupMenu, LocaleHandler.getString("ContextEditor.createContextMenuActions.invert"),
                am.get("invert"));
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
                LocaleHandler.getString("ContextEditor.createContextMenuActions.addObjectAbove"),
                am.get("addObjectAbove"));
        addMenuItem(objectCellPopupMenu,
                LocaleHandler.getString("ContextEditor.createContextMenuActions.addObjectBelow"),
                am.get("addObjectBelow"));
        
        addMenuItem(attributeCellPopupMenu,
                LocaleHandler.getString("ContextEditor.createContextMenuActions.renameAttribute"),
                am.get("renameAttribute"));
        addMenuItem(attributeCellPopupMenu,
                LocaleHandler.getString("ContextEditor.createContextMenuActions.removeAttribute"),
                am.get("removeAttribute"));
        addMenuItem(attributeCellPopupMenu,
                LocaleHandler.getString("ContextEditor.createContextMenuActions.addAttributeLeft"),
                am.get("addAttributeLeft"));
        addMenuItem(attributeCellPopupMenu,
                LocaleHandler.getString("ContextEditor.createContextMenuActions.addAttributeRight"),
                am.get("addAttributeRight"));
    }

    private void createMouseActions() {
        MouseAdapter mouseAdapter = new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                int i = matrix.rowAtPoint(e.getPoint());
                int j = matrix.columnAtPoint(e.getPoint());
                int clicks = e.getClickCount();
                if (clicks >= 2 && clicks % 2 == 0 && SwingUtilities.isLeftMouseButton(e)) {
                    if (i > 1 && j > 0) {  // (F1) ÉTAPE 2 : Changé i > 0 en i > 1
                        invokeAction(ContextEditor.this, new ToggleAction(i, j));
                    }
                }
            }

            public void mousePressed(MouseEvent e) {
                maybeShowPopup(e);
            }

            public void mouseReleased(MouseEvent e) {
                maybeShowPopup(e);
            }
        };
        matrix.addMouseListener(mouseAdapter);
        matrix.addMouseMotionListener(mouseAdapter);
    }

    private void maybeShowPopup(MouseEvent e) {
        int i = matrix.rowAtPoint(e.getPoint());
        int j = matrix.columnAtPoint(e.getPoint());
        lastActiveRowIndex = i;
        lastActiveColumnIndex = j;
        if (e.isPopupTrigger()) {
            if (i == 0 && j == 0) {
                // Don't show a context menu in the matrix corner
            } else if (i > 1 && j > 0) {  // (F1) CORRECTION 5 : Changé i > 0 en i > 1
                if (matrix.getSelectedColumn() <= 0 || matrix.getSelectedRow() <= 1) {  // (F1) Changé <= 0 en <= 1
                    matrix.selectCell(i, j);
                }
                cellPopupMenu.show(e.getComponent(), e.getX(), e.getY());
            } else if (j == 0) {
                objectCellPopupMenu.show(e.getComponent(), e.getX(), e.getY());
            } else {
                // (F1) NOUVEAU : Vérifier si c'est un groupe d'attributs
                AttributeGroup group = matrixModel.getGroupAtColumn(j);
                if (group != null) {
                    // C'est un groupe → afficher le menu groupe
                    groupHeaderPopupMenu.show(e.getComponent(), e.getX(), e.getY());
                } else {
                    // C'est un attribut normal
                    attributeCellPopupMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Actions (existantes + F1)
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    class CombineActions extends AbstractAction {
        Action first, second;

        CombineActions(Action first, Action second) {
            this.first = first;
            this.second = second;
        }

        public void actionPerformed(ActionEvent e) {
            invokeAction(e, first);
            invokeAction(e, second);
        }
    }

    class ExpandSelectionAction extends AbstractAction {
        int horizontal, vertical;

        ExpandSelectionAction(int horizontal, int vertical) {
            this.horizontal = horizontal;
            this.vertical = vertical;
        }

        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming)
                return;
            matrix.saveSelection();
            // (F1) CORRECTION 7 : Changé clamp(rowIndex + vertical, 1, ...) en clamp(rowIndex + vertical, 2, ...)
            int lastActiveRowIndexAfter = clamp(lastActiveRowIndex + vertical, 2, state.context.getObjectCount() + 1);
            int lastActiveColumnIndexAfter = clamp(lastActiveColumnIndex + horizontal, 1,
                    state.context.getAttributeCount());
            boolean wasRowChange = (lastActiveRowIndexAfter - lastActiveRowIndex) != 0;
            boolean wasColumnChange = (lastActiveColumnIndexAfter - lastActiveColumnIndex) != 0;
            boolean isNewRowIndexInsideOldSelection = lastActiveRowIndexAfter >= matrix.getLastSelectedRowsStartIndex()
                    && lastActiveRowIndexAfter <= matrix.getLastSelectedRowsEndIndex();
            boolean isNewColumnIndexInsideOldSelection = lastActiveColumnIndexAfter >= matrix
                    .getLastSelectedColumnsStartIndex()
                    && lastActiveColumnIndexAfter <= matrix.getLastSelectedColumnsEndIndex();
            if (wasRowChange) {
                if (isNewRowIndexInsideOldSelection) {
                    matrix.removeRowSelectionInterval(lastActiveRowIndex, lastActiveRowIndex);
                } else {
                    matrix.addRowSelectionInterval(lastActiveRowIndexAfter, lastActiveRowIndexAfter);
                }
                lastActiveRowIndex = lastActiveRowIndexAfter;
            }
            if (wasColumnChange) {
                if (isNewColumnIndexInsideOldSelection) {
                    matrix.removeColumnSelectionInterval(lastActiveColumnIndex, lastActiveColumnIndex);
                } else {
                    matrix.addColumnSelectionInterval(lastActiveColumnIndexAfter, lastActiveColumnIndexAfter);
                }
                lastActiveColumnIndex = lastActiveColumnIndexAfter;
            }
        }
    }

    class MoveObjectOrAttributeAction extends AbstractAction {
        int horizontal, vertical;

        MoveObjectOrAttributeAction(int horizontal, int vertical) {
            this.horizontal = horizontal;
            this.vertical = vertical;
        }

        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming)
                return;
            int lastActiveRowIndexAfter = clamp(lastActiveRowIndex + vertical, 1, state.context.getObjectCount());
            int lastActiveColumnIndexAfter = clamp(lastActiveColumnIndex + horizontal, 1,
                    state.context.getAttributeCount());
            boolean wasRowChange = (lastActiveRowIndexAfter - lastActiveRowIndex) != 0;
            boolean wasColumnChange = (lastActiveColumnIndexAfter - lastActiveColumnIndex) != 0;
            if (wasRowChange) {
                matrixModel.reorderRows(lastActiveRowIndex, lastActiveRowIndexAfter);
                matrixModel.fireTableDataChanged();
                lastActiveRowIndex = lastActiveRowIndexAfter;
                matrix.selectCell(lastActiveRowIndex, lastActiveColumnIndex);
                matrix.saveSelection();
            }
            if (wasColumnChange) {
                matrixModel.reorderColumns(lastActiveColumnIndex, lastActiveColumnIndexAfter);
                matrixModel.fireTableDataChanged();
                lastActiveColumnIndex = lastActiveColumnIndexAfter;
                matrix.selectCell(lastActiveRowIndex, lastActiveColumnIndex);
                matrix.saveSelection();
            }
        }
    }

    class MoveAction extends AbstractAction {
        int horizontal, vertical;

        MoveAction(int horizontal, int vertical) {
            this.horizontal = horizontal;
            this.vertical = vertical;
        }

        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming)
                return;
            // (F1) CORRECTION 6 : Changé clamp(rowIndex + vertical, 1, ...) en clamp(rowIndex + vertical, 2, ...)
            lastActiveRowIndex = clamp(lastActiveRowIndex + vertical, 2, state.context.getObjectCount() + 1);
            lastActiveColumnIndex = clamp(lastActiveColumnIndex + horizontal, 1, state.context.getAttributeCount());
            matrix.selectCell(lastActiveRowIndex, lastActiveColumnIndex);
        }
    }

    class MoveWithCarryAction extends AbstractAction {
        int horizontal, vertical;

        MoveWithCarryAction(int horizontal, int vertical) {
            this.horizontal = horizontal;
            this.vertical = vertical;
        }

        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming)
                return;
            if (state.context.getObjectCount() == 0 || state.context.getAttributeCount() == 0)
                return;
            int i = lastActiveRowIndex + vertical - 1;
            int j = lastActiveColumnIndex + horizontal - 1;
            while (true) {
                if (i < 0) {
                    j -= 1;
                    i = state.context.getObjectCount() - 1;
                    break;
                }
                if (j < 0) {
                    i -= 1;
                    j = state.context.getAttributeCount() - 1;
                }
                if (i >= state.context.getObjectCount()) {
                    j += 1;
                    i = 0;
                    break;
                }
                if (j >= state.context.getAttributeCount()) {
                    i += 1;
                    j = 0;
                }
                break;
            }
            i = mod(i, state.context.getObjectCount());
            j = mod(j, state.context.getAttributeCount());
            lastActiveRowIndex = i + 1;
            lastActiveColumnIndex = j + 1;
            matrix.selectCell(lastActiveRowIndex, lastActiveColumnIndex);
        }
    }

    class ToggleAction extends AbstractAction {
        int i, j;

        ToggleAction(int i, int j) {
            this.i = i;
            this.j = j;
        }

        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming)
                return;
            if (i <= 1 || j <= 0)  // (F1) CORRECTION 1 : Changé i <= 0 en i <= 1
                return;
            // (F1) CORRECTION 1 : Changé clamp(this.i, 1, ...) - 1 en clamp(this.i - 2, 0, ...)
            int i = clamp(this.i - 2, 0, state.context.getObjectCount() - 1);
            int j = clamp(this.j, 1, state.context.getAttributeCount()) - 1;
            state.saveConf();
            state.context.toggleAttributeForObject(state.context.getAttributeAtIndex(j), state.context
                    .getObjectAtIndex(i).getIdentifier());
            matrix.saveSelection();
            matrixModel.fireTableDataChanged();
            matrix.restoreSelection();
            state.contextChanged();
            state.getContextEditorUndoManager().makeRedoable();
        }
    }

    class ToggleActiveAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming)
                return;
            invokeAction(ContextEditor.this, new ToggleAction(lastActiveRowIndex, lastActiveColumnIndex));
        }
    }

    class SelectAllAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming)
                return;
            matrix.selectAll();
            matrix.saveSelection();
        }
    }

    class SelectNoneAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming)
                return;
            matrix.clearSelection();
            matrix.saveSelection();
        }
    }

    abstract class AbstractFillClearInvertAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming)
                return;
            int i1 = matrix.getSelectedRow() - 1;
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

    class FillAction extends AbstractFillClearInvertAction {
        void execute(int i1, int i2, int j1, int j2) {
            state.context.fill(i1, i2, j1, j2);
        }
    }

    class ClearAction extends AbstractFillClearInvertAction {
        void execute(int i1, int i2, int j1, int j2) {
            state.context.clear(i1, i2, j1, j2);
        }
    }

    class InvertAction extends AbstractFillClearInvertAction {
        void execute(int i1, int i2, int j1, int j2) {
            state.context.invert(i1, i2, j1, j2);
        }
    }

    class ClarifyObjectsAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) {
                return;
            }
            Long progressBarId = state.getStatusBar().startCalculation();
            ClarificationReductionWorker crw = new ClarificationReductionWorker(state, matrix, progressBarId, true,
                    false, false);
            crw.addPropertyChangeListener(new StatusBarPropertyChangeListener(progressBarId, state.getStatusBar()));
            state.getStatusBar().addCalculation(progressBarId, crw);
            crw.execute();
        }
    }

    class ClarifyAttributesAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) {
                return;
            }
            Long progressBarId = state.getStatusBar().startCalculation();
            ClarificationReductionWorker crw = new ClarificationReductionWorker(state, matrix, progressBarId, false,
                    false, false);
            crw.addPropertyChangeListener(new StatusBarPropertyChangeListener(progressBarId, state.getStatusBar()));
            state.getStatusBar().addCalculation(progressBarId, crw);
            crw.execute();
        }
    }

    class ReduceObjectsAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) {
                return;
            }
            Long progressBarId = state.getStatusBar().startCalculation();
            ClarificationReductionWorker crw = new ClarificationReductionWorker(state, matrix, progressBarId, true,
                    true, false);
            crw.addPropertyChangeListener(new StatusBarPropertyChangeListener(progressBarId, state.getStatusBar()));
            state.getStatusBar().addCalculation(progressBarId, crw);
            crw.execute();
        }
    }

    class ReduceAttributesAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) {
                return;
            }
            Long progressBarId = state.getStatusBar().startCalculation();
            ClarificationReductionWorker crw = new ClarificationReductionWorker(state, matrix, progressBarId, false,
                    true, false);
            crw.addPropertyChangeListener(new StatusBarPropertyChangeListener(progressBarId, state.getStatusBar()));
            state.getStatusBar().addCalculation(progressBarId, crw);
            crw.execute();
        }
    }

    class ReduceAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) {
                return;
            }
            Long progressBarId = state.getStatusBar().startCalculation();
            ClarificationReductionWorker crw = new ClarificationReductionWorker(state, matrix, progressBarId, false,
                    false, true);
            crw.addPropertyChangeListener(new StatusBarPropertyChangeListener(progressBarId, state.getStatusBar()));
            state.getStatusBar().addCalculation(progressBarId, crw);
            crw.execute();
        }
    }

    class TransposeAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) {
                return;
            }
            state.saveConf();
            state.context.transpose();
            matrixModel.fireTableStructureChanged();
            matrix.clearSelection();
            matrix.saveSelection();
            state.contextChanged();
            state.getContextEditorUndoManager().makeRedoable();
        }
    }

    class AddAttributeAtAction extends AbstractAction {
        int index;

        AddAttributeAtAction(int index) {
            this.index = index;
        }

        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) {
                return;
            }
            state.saveConf();
            matrix.saveSelection();
            addAttributeAt(index);
            matrix.restoreSelection();
            state.contextChanged();
            state.getContextEditorUndoManager().makeRedoable();
        }
    }

    class AddObjectAtAction extends AbstractAction {
        int index;

        AddObjectAtAction(int index) {
            this.index = index;
        }

        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) {
                return;
            }
            state.saveConf();
            matrix.saveSelection();
            addObjectAt(index);
            matrix.restoreSelection();
            state.contextChanged();
            state.getContextEditorUndoManager().makeRedoable();
        }
    }

    class AddAttributeAfterActiveAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) {
                return;
            }
            invokeAction(ContextEditor.this, new AddAttributeAtAction(lastActiveColumnIndex));
            lastActiveColumnIndex += 1;
            matrix.selectCell(lastActiveRowIndex, lastActiveColumnIndex);
            matrix.saveSelection();
        }
    }

    class AddAttributeBeforeActiveAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) {
                return;
            }
            invokeAction(ContextEditor.this, new AddAttributeAtAction(lastActiveColumnIndex - 1));
        }
    }

    class AddAttributeAtEndAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            boolean oldIsRenaming = matrix.isRenaming;
            matrix.isRenaming = false;
            invokeAction(ContextEditor.this, new AddAttributeAtAction(state.context.getAttributeCount()));
            matrix.isRenaming = oldIsRenaming;
        }
    }

    class AddObjectAfterActiveAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) {
                return;
            }
            invokeAction(ContextEditor.this, new AddObjectAtAction(lastActiveRowIndex));
            lastActiveRowIndex += 1;
            matrix.selectCell(lastActiveRowIndex, lastActiveColumnIndex);
            matrix.saveSelection();
        }
    }

    class AddObjectBeforeActiveAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) {
                return;
            }
            invokeAction(ContextEditor.this, new AddObjectAtAction(lastActiveRowIndex - 1));
        }
    }

    class AddObjectAtEndAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            boolean oldIsRenaming = matrix.isRenaming;
            matrix.isRenaming = false;
            invokeAction(ContextEditor.this, new AddObjectAtAction(state.context.getObjectCount()));
            matrix.isRenaming = oldIsRenaming;
        }
    }

    class RenameActiveObjectAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) {
                return;
            }
            if (state.context.getObjectCount() == 0) {
                return;
            }
            // (F1) CORRECTION 2 : Changé lastActiveRowIndex - 1 en lastActiveRowIndex - 2
            final String name = state.context.getObjectAtIndex(lastActiveRowIndex - 2).getIdentifier();
            final JTextField t = matrix.renameRowHeader(lastActiveRowIndex);
            Timer timer = new Timer(0, new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    t.setText(name);
                    t.selectAll();
                }
            });
            timer.setRepeats(false);
            timer.setInitialDelay(10);
            timer.start();
        }
    }

    class RenameActiveAttributeAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) {
                return;
            }
            if (state.context.getObjectCount() == 0) {
                return;
            }
            final String name = state.context.getAttributeAtIndex(lastActiveColumnIndex - 1);
            final JTextField t = matrix.renameColumnHeader(lastActiveColumnIndex);
            Timer timer = new Timer(0, new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    t.setText(name);
                    t.selectAll();
                }
            });
            timer.setRepeats(false);
            timer.setInitialDelay(10);
            timer.start();
        }
    }

    class RemoveActiveObjectAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) {
                return;
            }
            if (state.context.getObjectCount() == 0) {
                return;
            }
            matrix.saveSelection();
            try {
                state.saveConf();
                // (F1) CORRECTION 3 : Changé lastActiveRowIndex - 1 en lastActiveRowIndex - 2
                state.context.removeObject(state.context.getObjectAtIndex(lastActiveRowIndex - 2).getIdentifier());
                if (lastActiveRowIndex - 1 >= state.context.getObjectCount()) {
                    lastActiveRowIndex--;
                }
            } catch (IllegalObjectException e1) {
                e1.printStackTrace();
            }
            matrixModel.fireTableStructureChanged();
            matrix.invalidate();
            matrix.repaint();
            matrix.restoreSelection();
            state.contextChanged();
            state.getContextEditorUndoManager().makeRedoable();
        }
    }

    class RemoveActiveAttributeAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) {
                return;
            }
            if (state.context.getAttributeCount() == 0) {
                return;
            }
            matrix.saveSelection();
            state.saveConf();
            state.context.removeAttribute(state.context.getAttributeAtIndex(lastActiveColumnIndex - 1));
            matrix.updateColumnWidths(lastActiveColumnIndex);
            if (lastActiveColumnIndex - 1 >= state.context.getAttributeCount()) {
                lastActiveColumnIndex--;
            }
            matrixModel.fireTableStructureChanged();
            matrix.invalidate();
            matrix.repaint();
            matrix.restoreSelection();
            state.contextChanged();
            state.getContextEditorUndoManager().makeRedoable();
        }
    }

    class RemoveSelectedObjectsAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) {
                return;
            }
            if (state.context.getAttributeCount() == 0) {
                return;
            }
            matrix.saveSelection();
            // (F1) CORRECTION 4 : Changé - 1 en - 2
            int i = Math.min(matrix.getLastSelectedRowsStartIndex(), matrix.getLastSelectedRowsEndIndex()) - 2;
            int d = Math.abs(matrix.getLastSelectedRowsStartIndex() - matrix.getLastSelectedRowsEndIndex()) + 1;
            state.saveConf();
            for (int unused = 0; unused < d; unused++) {
                try {
                    state.context.removeObject(state.context.getObjectAtIndex(i).getIdentifier());
                } catch (IllegalObjectException e1) {
                    e1.printStackTrace();
                }
            }
            matrixModel.fireTableStructureChanged();
            matrix.invalidate();
            matrix.repaint();
            state.contextChanged();
            state.getContextEditorUndoManager().makeRedoable();
        }
    }

    class RemoveSelectedAttributesAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) {
                return;
            }
            if (state.context.getAttributeCount() == 0) {
                return;
            }
            matrix.saveSelection();
            int i = Math.min(matrix.getLastSelectedColumnsStartIndex(), matrix.getLastSelectedColumnsEndIndex()) - 1;
            int d = Math.abs(matrix.getLastSelectedColumnsStartIndex() - matrix.getLastSelectedColumnsEndIndex()) + 1;
            state.saveConf();
            for (int unused = 0; unused < d; unused++) {
                state.context.removeAttribute(state.context.getAttributeAtIndex(i));
                matrix.updateColumnWidths(i + 1);
            }
            matrixModel.fireTableStructureChanged();
            matrix.invalidate();
            matrix.repaint();
            state.contextChanged();
            state.getContextEditorUndoManager().makeRedoable();
        }
    }

    class CompactAction extends AbstractAction implements ItemListener {
        public void itemStateChanged(ItemEvent e) {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                state.guiConf.compactMatrix = true;
                matrix.compact();
            } else {
                state.guiConf.compactMatrix = false;
                matrix.uncompact();
            }
        }

        public void actionPerformed(ActionEvent e) {
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // (F1) ACTIONS pour Fonctionnalité 1 : Groupes d'Attributs
    // ═════════════════════════════════════════════════════════════════════════

    class GroupSelectedAttributesAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            java.util.Collection<String> availableAttrs = state.context.getUngroupedAttributes();
            
            if (availableAttrs.isEmpty()) {
                JOptionPane.showMessageDialog(
                    ContextEditor.this,
                    "All attributes are already grouped!",
                    "No Attributes Available",
                    JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            AttributeSelectionDialog dialog = new AttributeSelectionDialog(
            	    ContextEditor.this,
            	    new java.util.ArrayList<String>(availableAttrs));
            
            dialog.setVisible(true);
            
            if (dialog.isConfirmed()) {
                java.util.Set<String> selectedAttrs = dialog.getSelectedAttributes();
                
                if (selectedAttrs.isEmpty()) {
                    JOptionPane.showMessageDialog(
                        ContextEditor.this,
                        "Please select at least one attribute!",
                        "No Selection",
                        JOptionPane.WARNING_MESSAGE);
                    return;
                }

                String groupName = JOptionPane.showInputDialog(
                    ContextEditor.this,
                    "Enter group name:",
                    "Create Group",
                    JOptionPane.QUESTION_MESSAGE);

                if (groupName == null || groupName.isEmpty()) {
                    return;
                }

                String groupNameUpper = groupName.trim().toUpperCase();

                if (state.context.getAttributeGroupManager().groupNameExists(groupNameUpper)) {
                    JOptionPane.showMessageDialog(
                        ContextEditor.this,
                        "A group with name '" + groupNameUpper + "' already exists!",
                        "Duplicate Group Name",
                        JOptionPane.ERROR_MESSAGE);
                    return;
                }

                state.saveConf();
                String groupId = state.context.createAttributeGroup(groupNameUpper, selectedAttrs);
                
                if (groupId != null) {
                    state.context.reorganizeAttributesForGroups();
                    matrixModel.fireTableStructureChanged();
                    matrix.invalidate();
                    matrix.repaint();
                    state.contextChanged();
                    state.getContextEditorUndoManager().makeRedoable();
                    
                    JOptionPane.showMessageDialog(
                        ContextEditor.this,
                        "Group '" + groupNameUpper + "' created with " + selectedAttrs.size() + " attributes!",
                        "Success",
                        JOptionPane.INFORMATION_MESSAGE);
                }
            }
        }
    }

 // (F1) CORRECTION : UngroupAttributeAction - Avec dialog pour choisir le groupe à dégrouper
    class UngroupAttributeAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) {
                return;
            }

            // Récupérer tous les groupes disponibles
            java.util.List<AttributeGroup> allGroups = new java.util.ArrayList<AttributeGroup>(
                state.context.getAllAttributeGroups());

            if (allGroups.isEmpty()) {
                JOptionPane.showMessageDialog(
                    ContextEditor.this,
                    "No attribute groups to ungroup!",
                    "No Groups",
                    JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            // Créer une liste d'options
            java.util.List<String> options = new java.util.ArrayList<String>();
            java.util.Map<String, AttributeGroup> groupMap = new java.util.HashMap<String, AttributeGroup>();

            for (AttributeGroup group : allGroups) {
                String label = group.getGroupName() + " (" + group.getAttributeCount() + " attributes)";
                options.add(label);
                groupMap.put(label, group);
            }

            // Dialog de sélection
            String[] optionsArray = options.toArray(new String[0]);
            String selected = (String) JOptionPane.showInputDialog(
                ContextEditor.this,
                "Select a group to ungroup:",
                "Ungroup Attributes",
                JOptionPane.QUESTION_MESSAGE,
                null,
                optionsArray,
                optionsArray[0]);

            if (selected == null) {
                return; // Utilisateur a annulé
            }

            AttributeGroup selectedGroup = groupMap.get(selected);
            if (selectedGroup == null) {
                return;
            }

            // Confirmer avant de dégrouper
            int confirm = JOptionPane.showConfirmDialog(
                ContextEditor.this,
                "Remove group '" + selectedGroup.getGroupName() + "' and ungroup " + 
                selectedGroup.getAttributeCount() + " attributes?",
                "Confirm Ungroup",
                JOptionPane.YES_NO_OPTION);

            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }

            // Dégrouper
            state.saveConf();
            state.context.removeAttributeGroup(selectedGroup.getGroupId());
            matrixModel.fireTableStructureChanged();
            matrix.invalidate();
            matrix.repaint();
            state.contextChanged();
            state.getContextEditorUndoManager().makeRedoable();

            JOptionPane.showMessageDialog(
                ContextEditor.this,
                "Group '" + selectedGroup.getGroupName() + "' has been removed.\n" +
                "Its " + selectedGroup.getAttributeCount() + " attributes are now ungrouped.",
                "Ungroup Successful",
                JOptionPane.INFORMATION_MESSAGE);
        }
    }

    class RenameGroupAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) {
                return;
            }

            AttributeGroup group = matrixModel.getGroupAtColumn(lastActiveColumnIndex);
            if (group == null) {
                return;
            }

            String newName = JOptionPane.showInputDialog(
                ContextEditor.this,
                "Enter new group name:",
                group.getGroupName(),
                JOptionPane.QUESTION_MESSAGE);

            if (newName != null && !newName.isEmpty()) {
                state.saveConf();
                state.context.getAttributeGroupManager().renameGroup(group.getGroupId(), newName);
                matrixModel.fireTableStructureChanged();
                matrix.invalidate();
                matrix.repaint();
                state.contextChanged();
                state.getContextEditorUndoManager().makeRedoable();
            }
        }
    }

    class ToggleGroupExpansionAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) {
                return;
            }

            AttributeGroup group = matrixModel.getGroupAtColumn(lastActiveColumnIndex);
            if (group == null) {
                return;
            }

            matrixModel.toggleGroupExpansion(group.getGroupId());
            matrix.invalidate();
            matrix.repaint();
        }
    }

    // (F1) CORRECTION 8 : GenerateLatticeForGroupAction - Corrigée avec dialog
    class GenerateLatticeForGroupAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            if (matrix.isRenaming) {
                return;
            }

            java.util.List<AttributeGroup> allGroups = new java.util.ArrayList<AttributeGroup>(
                state.context.getAllAttributeGroups());
            java.util.Collection<String> ungroupedAttrs = state.context.getUngroupedAttributes();

            java.util.List<String> options = new java.util.ArrayList<String>();
            java.util.Map<String, AttributeGroup> groupMap = new java.util.HashMap<String, AttributeGroup>();

            for (AttributeGroup group : allGroups) {
                String label = group.getGroupName() + " (" + group.getAttributeCount() + " attributes)";
                options.add(label);
                groupMap.put(label, group);
            }

            for (String attr : ungroupedAttrs) {
                options.add(attr + " (single attribute)");
                groupMap.put(attr + " (single attribute)", null);
            }

            if (options.isEmpty()) {
                JOptionPane.showMessageDialog(
                    ContextEditor.this,
                    "No groups or attributes available.",
                    "Empty Context",
                    JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            String[] optionsArray = options.toArray(new String[0]);
            String selected = (String) JOptionPane.showInputDialog(
                ContextEditor.this,
                "Select a group to generate lattice for:",
                "Generate Lattice for Group",
                JOptionPane.QUESTION_MESSAGE,
                null,
                optionsArray,
                optionsArray[0]);

            if (selected == null) {
                return;
            }

            AttributeGroup selectedGroup = groupMap.get(selected);

            if (selectedGroup == null) {
                String attrName = selected.replace(" (single attribute)", "");
                JOptionPane.showMessageDialog(
                    ContextEditor.this,
                    "Generating lattice for single attribute: " + attrName,
                    "Single Attribute Lattice",
                    JOptionPane.INFORMATION_MESSAGE);
                
                state.setCurrentGroupIdForLattice(null);
                state.contextChanged();
                return;
            }

            state.setCurrentGroupIdForLattice(selectedGroup.getGroupId());
            state.contextChanged();

            JOptionPane.showMessageDialog(
                ContextEditor.this,
                "Lattice generated for group: " + selectedGroup.getGroupName() + "\n" +
                "Displaying filtered lattice with " + selectedGroup.getAttributeCount() + " attributes.",
                "Lattice Generated",
                JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Helper functions
    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void addAttributeAt(final int i) {
        String collisionFreeName = "attr" + i;
        while (true) {
            if (!state.context.existsAttributeAlready(collisionFreeName)) {
                break;
            }
            collisionFreeName = collisionFreeName + "'";
        }
        state.context.addAttributeAt(collisionFreeName, i);
        matrixModel.fireTableStructureChanged();
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                matrix.renameColumnHeader(i + 1);
            }
        });
    }

    private void addObjectAt(final int i) {
        String collisionFreeName = "obj" + i;
        while (true) {
            if (!state.context.existsObjectAlready(collisionFreeName)) {
                break;
            }
            collisionFreeName = collisionFreeName + "'";
        }
        FullObject<String, String> newObject = new FullObject<>(collisionFreeName);
        state.context.addObjectAt(newObject, i);
        matrixModel.fireTableStructureChanged();
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                matrix.renameRowHeader(i + 1);
            }
        });
    }
    
    /**
     * (F1) ÉTAPE 1 : Dialog pour sélectionner visuellement les attributs à grouper
     */
    @SuppressWarnings("serial")
    private class AttributeSelectionDialog extends JDialog {
        private boolean confirmed = false;
        private java.util.Set<String> selectedAttributes = new java.util.HashSet<String>();
        private java.util.List<JCheckBox> checkboxes = new java.util.ArrayList<JCheckBox>();
        private java.awt.Component parentComponent;
        
        public AttributeSelectionDialog(ContextEditor contextEditor, java.util.List<String> availableAttributes) {
            super(SwingUtilities.getWindowAncestor(contextEditor), 
                  "Select Attributes to Group", 
                  JDialog.ModalityType.APPLICATION_MODAL);
            this.parentComponent = contextEditor;
            setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            
            JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
            mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
            
            JLabel label = new JLabel("Select attributes to group:");
            mainPanel.add(label, BorderLayout.NORTH);
            
            JPanel checkboxPanel = new JPanel();
            checkboxPanel.setLayout(new BoxLayout(checkboxPanel, BoxLayout.Y_AXIS));
            
            for (String attr : availableAttributes) {
                JCheckBox checkbox = new JCheckBox(attr);
                checkboxes.add(checkbox);
                checkboxPanel.add(checkbox);
            }
            
            JScrollPane scrollPane = new JScrollPane(checkboxPanel);
            scrollPane.setPreferredSize(new Dimension(300, 200));
            mainPanel.add(scrollPane, BorderLayout.CENTER);
            
            JPanel buttonPanel = new JPanel();
            JButton okButton = new JButton("OK");
            JButton cancelButton = new JButton("Cancel");
            
            okButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    confirmed = true;
                    for (JCheckBox checkbox : checkboxes) {
                        if (checkbox.isSelected()) {
                            selectedAttributes.add(checkbox.getText());
                        }
                    }
                    dispose();
                }
            });
            
            cancelButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    confirmed = false;
                    dispose();
                }
            });
            
            buttonPanel.add(okButton);
            buttonPanel.add(cancelButton);
            mainPanel.add(buttonPanel, BorderLayout.SOUTH);
            
            add(mainPanel);
            pack();
            setLocationRelativeTo(parentComponent);
        }

        public boolean isConfirmed() {
            return confirmed;
        }

        public java.util.Set<String> getSelectedAttributes() {
            return selectedAttributes;
        }
    }
}