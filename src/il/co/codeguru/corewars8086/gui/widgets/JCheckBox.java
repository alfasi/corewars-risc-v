package il.co.codeguru.corewars8086.gui.widgets;

//import com.google.gwt.dom.client.InputElement;

import elemental2.dom.HTMLInputElement;

public class JCheckBox extends JComponent<HTMLInputElement>
{
    public JCheckBox(String id, String text) {
        super(id);
        setOnChange();
    }
    public JCheckBox() {
    }    

    public boolean isSelected() {
        return m_element.checked;
    }
    public void setSelected(boolean v) {
        m_element.checked = v;
    }

    
}