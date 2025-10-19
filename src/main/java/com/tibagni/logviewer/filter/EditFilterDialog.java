package com.tibagni.logviewer.filter;

import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;
import com.tibagni.logviewer.ServiceLocator;
import com.tibagni.logviewer.filter.regex.RegexEditorDialog;
import com.tibagni.logviewer.log.LogLevel;
import com.tibagni.logviewer.theme.LogViewerThemeManager;
import com.tibagni.logviewer.util.StringUtils;
import com.tibagni.logviewer.util.scaling.UIScaleUtils;
import com.tibagni.logviewer.util.layout.GBConstraintsBuilder;
import com.tibagni.logviewer.view.ButtonsPane;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.colorchooser.AbstractColorChooserPanel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.util.Random;

public class EditFilterDialog extends JDialog implements ButtonsPane.Listener {
  private static final Color[] INITIAL_COLORS_LIGHT = new Color[]{
      Color.darkGray,
      new Color(102, 102, 0),
      new Color(0, 102, 153),
      new Color(0, 102, 102),
      new Color(102, 0, 0),
      new Color(26, 69, 73),
      new Color(88, 24, 88),
      new Color(97, 49, 2)
  };

  private static final Color[] INITIAL_COLORS_DARK = new Color[]{
      Color.blue,
      Color.red,
      Color.yellow,
      Color.cyan,
      Color.green,
      Color.pink,
      new Color(208, 208, 119),
      new Color(83, 199, 246),
      new Color(8, 248, 248),
      new Color(222, 143, 143),
      new Color(111, 210, 255),
      new Color(255, 6, 250),
      new Color(252, 156, 106)
  };

  private ButtonsPane buttonsPane;
  private JPanel contentPane;
  private JLabel nameLbl;
  private JTextField nameTxt;
  private JLabel regexLbl;
  private JTextField regexTxt;
  private JLabel colorLbl;
  private JLabel caseSensitiveLbl;
  private JCheckBox caseSensitiveCbx;
  private JLabel verbosityLbl;
  private JComboBox<LogLevel> verbosityCombo;
  private JButton regexEditorBtn;
  private JColorChooser colorChooser;
  
  // Multi-keyword search related components
  private JLabel filterTypeLbl;
  private JComboBox<String> filterTypeCombo;
  private JLabel keywordsLbl;
  private JPanel keywordsPanel;
  private JTextField keywordField1;
  private JTextField keywordField2;
  private JTextField keywordField3;
  private JTextField keywordField4;

  private Filter filter;
  private String previewText;
  private final LogViewerThemeManager themeManager;

  private final DocumentListener regexDocumentListener = new DocumentListener() {
    @Override
    public void insertUpdate(DocumentEvent e) {
      nameTxt.setText(regexTxt.getText());
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
      nameTxt.setText(regexTxt.getText());
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
      nameTxt.setText(regexTxt.getText());
    }
  };

  private EditFilterDialog(Frame owner, Filter editingFilter) {
    this(owner, editingFilter, null);
  }

  private EditFilterDialog(Frame owner, Filter editingFilter, String preDefinedText) {
    super(owner);
    previewText = preDefinedText;
    themeManager = ServiceLocator.INSTANCE.getThemeManager();
    buildUi();

    setContentPane(contentPane);
    setModal(true);
    buttonsPane.setDefaultButtonOk();

    regexEditorBtn.addActionListener(e -> onEditRegex());
    filterTypeCombo.addActionListener(e -> onFilterTypeChanged());

    colorChooser.setColor(getInitialColor());

    // call onCancel() when cross is clicked
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        onCancel();
      }
    });

    // call onCancel() on ESCAPE
    contentPane.registerKeyboardAction(e -> onCancel(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
        JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    boolean nameIsPattern = true;
    if (editingFilter != null) {
      filter = editingFilter;
      nameTxt.setText(filter.getName());
      colorChooser.setColor(filter.getColor());
      caseSensitiveCbx.setSelected(filter.isCaseSensitive());
      verbosityCombo.setSelectedItem(filter.getVerbosity());
      
      if (filter.isMultiKeywordFilter()) {
        filterTypeCombo.setSelectedItem("Multi-Keyword Search");
        String[] keywords = filter.getKeywords();
        loadKeywordsToFields(keywords);
      } else {
        filterTypeCombo.setSelectedItem("Regular Expression");
        regexTxt.setText(filter.getPatternString());
        regexTxt.selectAll();
        nameIsPattern = filter.nameIsPattern();
      }
    }

    onFilterTypeChanged();

    if (nameIsPattern && filter != null && !filter.isMultiKeywordFilter()) {
      regexTxt.getDocument().addDocumentListener(regexDocumentListener);
      nameTxt.setEnabled(false);
      nameTxt.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          if (!nameTxt.isEnabled()) {
            regexTxt.getDocument().removeDocumentListener(regexDocumentListener);
            nameTxt.setEnabled(true);
            nameTxt.requestFocus();
            nameTxt.selectAll();
          }
        }
      });

      // Adjust the size according to the content after everything is populated
      contentPane.setPreferredSize(contentPane.getPreferredSize());
      contentPane.validate();
    }

     SwingUtilities.invokeLater(() -> {
       if (filterTypeCombo.getSelectedItem().equals("Multi-Keyword Search")) {
         keywordField1.requestFocus();
       } else {
         regexTxt.requestFocus();
       }
     });

    if (!StringUtils.isEmpty(preDefinedText)) {
      regexTxt.setText(preDefinedText);
      addWindowListener(new WindowAdapter() {
        @Override
        public void windowOpened(WindowEvent e) {
          regexEditorBtn.doClick();
        }
      });
    }
  }

  @Override
  public void onOk() {
    // add your code here
    Color selectedColor = colorChooser.getColor();
    String name = nameTxt.getText();
    boolean caseSensitive = caseSensitiveCbx.isSelected();
    LogLevel verbosity = (LogLevel) verbosityCombo.getSelectedItem();

    try {
      if (filter == null) {
        if (filterTypeCombo.getSelectedItem().equals("Multi-Keyword Search")) {
          String[] keywords = parseKeywords();
          filter = new Filter(name, keywords, selectedColor, verbosity, caseSensitive);
        } else {
          String pattern = regexTxt.getText();
          filter = new Filter(name, pattern, selectedColor, verbosity, caseSensitive);
        }
      } else {
        if (filterTypeCombo.getSelectedItem().equals("Multi-Keyword Search")) {
          String[] keywords = parseKeywords();
          filter.updateMultiKeywordFilter(name, keywords, selectedColor, verbosity, caseSensitive);
        } else {
          String pattern = regexTxt.getText();
          filter.updateFilter(name, pattern, selectedColor, verbosity, caseSensitive);
        }
      }
    } catch (FilterException e) {
      JOptionPane.showConfirmDialog(this, e.getMessage(), "Error...",
          JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE);
      return;
    }

    dispose();
  }

  @Override
  public void onCancel() {
    filter = null;
    dispose();
  }

  private void onEditRegex() {
    RegexEditorDialog.Result edited = RegexEditorDialog.showEditRegexDialog(this, this,
        regexTxt.getText(), previewText, caseSensitiveCbx.isSelected());

    if (edited != null) {
      regexTxt.setText(edited.pattern);
      caseSensitiveCbx.setSelected(edited.caseSensitive);
    }
  }

    private void onFilterTypeChanged() {
        boolean isMultiKeyword = filterTypeCombo.getSelectedItem().equals("Multi-Keyword Search");
    
    regexLbl.setVisible(!isMultiKeyword);
    regexTxt.setVisible(!isMultiKeyword);
    // Keep Editor button visible to maintain layout space
    // regexEditorBtn.setVisible(!isMultiKeyword);
    
    keywordsLbl.setVisible(isMultiKeyword);
    keywordsPanel.setVisible(isMultiKeyword);
    
    // Adjust layout
    contentPane.revalidate();
    contentPane.repaint();
  }

  private String[] parseKeywords() {
    java.util.List<String> keywords = new java.util.ArrayList<>();
    JTextField[] fields = {keywordField1, keywordField2, keywordField3, keywordField4};
    for (JTextField field : fields) {
      String text = field.getText().trim();
      if (!StringUtils.isEmpty(text)) {
        keywords.add(text);
      }
    }
    return keywords.toArray(new String[0]);
  }
  
  private void loadKeywordsToFields(String[] keywords) {
    // Clear all fields first
    keywordField1.setText("");
    keywordField2.setText("");
    keywordField3.setText("");
    keywordField4.setText("");
    
    // Load keywords into fields
    if (keywords != null && keywords.length > 0) {
      JTextField[] fields = {keywordField1, keywordField2, keywordField3, keywordField4};
      int fieldIndex = 0;
      for (String keyword : keywords) {
        if (fieldIndex < fields.length) {
          fields[fieldIndex].setText(keyword);
          fieldIndex++;
        }
      }
    }
  }

  private AbstractColorChooserPanel getSwatchPanel(AbstractColorChooserPanel[] panels) {
    for (AbstractColorChooserPanel colorPanel : panels) {
      if (colorPanel.getClass().getName().contains("DefaultSwatchChooserPanel")) {
        return colorPanel;
      }
    }

    return null;
  }

  private Color getInitialColor() {
    // Set a random color for the filter initially
    final Random r = new Random();
    Color[] colors = themeManager.isDark() ? INITIAL_COLORS_DARK : INITIAL_COLORS_LIGHT;
    return colors[r.nextInt(colors.length)];
  }

  public static Filter showEditFilterDialog(Frame parent, Filter editingFilter) {
    EditFilterDialog dialog = new EditFilterDialog(parent, editingFilter);
    dialog.pack();
    dialog.setLocationRelativeTo(parent);
    dialog.setVisible(true);

    return dialog.filter;
  }

  public static Filter showEditFilterDialog(Frame parent) {
    return showEditFilterDialog(parent, null);
  }

  // This is used to create a Filter from an existing predefined String
  // It will open the Edit Dialog directly on the RegEx Editor
  public static Filter showEditFilterDialogWithText(Frame parent, String preDefinedText) {
    EditFilterDialog dialog = new EditFilterDialog(parent, null, preDefinedText);
    dialog.pack();
    dialog.setLocationRelativeTo(parent);
    dialog.setVisible(true);

    return dialog.filter;
  }

  private void buildUi() {
    contentPane = new JPanel();
    contentPane.setLayout(new GridBagLayout());
    contentPane.setBorder(BorderFactory.createEmptyBorder(UIScaleUtils.dip(10),
            UIScaleUtils.dip(10),
            UIScaleUtils.dip(10),
            UIScaleUtils.dip(10)));

    buttonsPane = new ButtonsPane(ButtonsPane.ButtonsMode.OK_CANCEL, this);
    contentPane.add(buttonsPane,
        new GBConstraintsBuilder()
            .withGridx(0)
            .withGridy(1)
            .withWeightx(1.0)
            .withFill(GridBagConstraints.BOTH)
            .build());

    contentPane.add(buildEditPane(),
        new GBConstraintsBuilder()
            .withGridx(0)
            .withGridy(0)
            .withWeightx(1.0)
            .withWeighty(1.0)
            .withFill(GridBagConstraints.BOTH)
            .build());
  }

  private JPanel buildEditPane() {
    final JPanel editPane = new JPanel();
    editPane.setLayout(new FormLayout(
        "fill:d:noGrow,left:4dlu:noGrow,fill:d:grow,left:4dlu:noGrow,fill:max(d;4px):noGrow,left:4dlu:noGrow,fill:max(d;4px):noGrow,left:4dlu:noGrow,fill:max(d;4px):noGrow",
        "center:d:noGrow,top:3dlu:noGrow,center:max(d;4px):noGrow,top:3dlu:noGrow,center:max(d;4px):noGrow,top:3dlu:noGrow,center:max(d;4px):noGrow,top:3dlu:noGrow,center:max(d;4px):noGrow,top:3dlu:noGrow,center:max(d;4px):noGrow,top:3dlu:noGrow,center:max(d;4px):noGrow,top:3dlu:noGrow,center:max(d;4px):noGrow"));

    nameLbl = new JLabel();
    nameLbl.setText("Filter name:");
    nameLbl.setToolTipText("Give a name to your filter to appear on the filters list");
    CellConstraints cc = new CellConstraints();
    editPane.add(nameLbl, cc.xy(1, 1));
    nameTxt = new JTextField();
    nameTxt.setEnabled(true);
    editPane.add(nameTxt, cc.xy(3, 1, CellConstraints.FILL, CellConstraints.DEFAULT));

    // Filter type selection
    filterTypeLbl = new JLabel();
    filterTypeLbl.setText("Filter type:");
    filterTypeLbl.setToolTipText("Choose the type of filter");
    editPane.add(filterTypeLbl, cc.xy(1, 3));
    filterTypeCombo = new JComboBox<>();
        filterTypeCombo.addItem("Regular Expression");
        filterTypeCombo.addItem("Multi-Keyword Search");
    editPane.add(filterTypeCombo, cc.xy(3, 3, CellConstraints.FILL, CellConstraints.DEFAULT));

    // Regular expression related components
    regexLbl = new JLabel();
    regexLbl.setText("Regex:");
    regexLbl.setToolTipText("The regular expression of your filter");
    editPane.add(regexLbl, cc.xy(1, 5));
    regexTxt = new JTextField();
    editPane.add(regexTxt, cc.xy(3, 5, CellConstraints.FILL, CellConstraints.DEFAULT));
    regexEditorBtn = new JButton();
    regexEditorBtn.setText("Editor");
    regexEditorBtn.setToolTipText("Open the regex editor window");
    editPane.add(regexEditorBtn, cc.xy(5, 5));

    // Multi-keyword search related components
    keywordsLbl = new JLabel();
    keywordsLbl.setText("Keywords:");
    keywordsLbl.setToolTipText("Enter keywords. All keywords must be present in the log line.");
    editPane.add(keywordsLbl, cc.xy(1, 5));
    
    // Create 4 keyword input fields in a panel with custom layout
    keywordsPanel = new JPanel();
    keywordsPanel.setLayout(new java.awt.BorderLayout());
    
    // Create a sub-panel for the 4 input fields with horizontal layout
    JPanel keywordsInputPanel = new JPanel();
    keywordsInputPanel.setLayout(new java.awt.BorderLayout());
    
    // Create the 4 input fields
    keywordField1 = new JTextField();
    keywordField1.setPreferredSize(new java.awt.Dimension(150, 22));
    keywordField1.setToolTipText("Enter keyword 1");
    
    keywordField2 = new JTextField();
    keywordField2.setPreferredSize(new java.awt.Dimension(150, 22));
    keywordField2.setToolTipText("Enter keyword 2");
    
    keywordField3 = new JTextField();
    keywordField3.setPreferredSize(new java.awt.Dimension(150, 22));
    keywordField3.setToolTipText("Enter keyword 3");
    
    keywordField4 = new JTextField();
    keywordField4.setPreferredSize(new java.awt.Dimension(150, 22));
    keywordField4.setToolTipText("Enter keyword 4");
    
    // Create a horizontal box to hold the 4 input fields with 20px spacing
    Box horizontalBox = Box.createHorizontalBox();
    horizontalBox.add(keywordField1);
    horizontalBox.add(Box.createHorizontalStrut(20));
    horizontalBox.add(keywordField2);
    horizontalBox.add(Box.createHorizontalStrut(20));
    horizontalBox.add(keywordField3);
    horizontalBox.add(Box.createHorizontalStrut(20));
    horizontalBox.add(keywordField4);
    
    keywordsInputPanel.add(horizontalBox, java.awt.BorderLayout.CENTER);
    keywordsPanel.add(keywordsInputPanel, java.awt.BorderLayout.CENTER);
    editPane.add(keywordsPanel, cc.xyw(3, 5, 1, CellConstraints.FILL, CellConstraints.DEFAULT));

    caseSensitiveLbl = new JLabel();
    caseSensitiveLbl.setText("Case sensitive:");
    editPane.add(caseSensitiveLbl, cc.xy(1, 7));
    caseSensitiveCbx = new JCheckBox();
    caseSensitiveCbx.setText("Enable case sensitive for this filter");
    editPane.add(caseSensitiveCbx, cc.xy(3, 7));

    verbosityLbl = new JLabel();
    verbosityLbl.setText("Verbosity");
    editPane.add(verbosityLbl, cc.xy(1, 9));
    verbosityCombo = new JComboBox<>();
    for (LogLevel level : LogLevel.values()) {
      verbosityCombo.addItem(level);
    }
    editPane.add(verbosityCombo, cc.xy(3, 9));

    colorLbl = new JLabel();
    colorLbl.setText("Color:");
    colorLbl.setToolTipText("Choose a color to differentiate your filter");
    editPane.add(colorLbl, cc.xy(1, 11));
    colorChooser = new JColorChooser();

    // Show a simple text field for preview
    JTextField preview = new JTextField("Filtered text color preview");
    preview.setBorder(new EmptyBorder(UIScaleUtils.dip(5),
            UIScaleUtils.dip(15),
            UIScaleUtils.dip(5),
            UIScaleUtils.dip(15)));
    colorChooser.setPreviewPanel(preview);
    editPane.add(colorChooser, cc.xy(3, 11));

    return editPane;
  }
}
