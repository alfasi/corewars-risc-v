package il.co.codeguru.corewars8086.gui;

import il.co.codeguru.corewars8086.war.*;
import com.google.gwt.animation.client.AnimationScheduler;
import com.google.gwt.user.client.Window;

import il.co.codeguru.corewars8086.gui.widgets.*;
//import java.awt.*;
//import java.awt.event.*;
import java.io.IOException;

import static il.co.codeguru.corewars8086.gui.CompetitionWindow.call_gwtStart;
//import javax.swing.*;

/**
 * @author BS
 */
public class CompetitionWindow extends JFrame
    implements ScoreEventListener, ActionListener, CompetitionEventListener {
	private static final long serialVersionUID = 1L;
	
	private Competition competition;
    private ColumnGraph columnGraph;

    // widgets
    private JButton runWarButton;
    private JLabel warCounterDisplay;
    private JCheckBox showBattleCheckBox;
    private JTextField battlesPerGroupField;
    //private JTextField warriorsPerGroupField;
    private WarFrame battleFrame;

    private int warCounter;
    private int totalWars;
    //private Thread warThread;
	private boolean competitionRunning;
	
	private JTextField seed;

    private JCheckBox startPausedCheckBox;
    private boolean m_isBattleShown;

    private CodeEditor m_codeEditor;
    private PlayersPanel m_playersPanel;

    public boolean isBattleShown() {
        return m_isBattleShown;
    }

    public CompetitionWindow() throws IOException {
        super("CodeGuru Extreme - Competition Viewer");
        getContentPane().setLayout(new BorderLayout());
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        competition = new Competition();
        competition.addCompetitionEventListener(this);
        WarriorRepository warriorRepository = competition
                        .getWarriorRepository();
        warriorRepository.addScoreEventListener(this);
        columnGraph = new ColumnGraph(warriorRepository.getGroupNames());
        getContentPane().add(columnGraph, BorderLayout.CENTER);
        // -------------
        JPanel controlArea = new JPanel();
        controlArea.setLayout(new BoxLayout(controlArea, BoxLayout.Y_AXIS));
        // -------------- Button Panel
        JPanel buttonPanel = new JPanel();
        runWarButton = new JButton("runWarButton", "<html><font color=red>Start!</font></html>");
        runWarButton.addActionListener(this);
        buttonPanel.add(runWarButton);
        warCounterDisplay = new JLabel("");
        buttonPanel.add(warCounterDisplay);
        buttonPanel.add(Box.createHorizontalStrut(30));
        showBattleCheckBox = new JCheckBox("showBattleCheckBox", "Show session on start");
		showBattleCheckBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
                m_isBattleShown = showBattleCheckBox.isSelected();
                Console.log("isBattleShown= " + (m_isBattleShown?"true":"false") );
            }
		});        
        buttonPanel.add(showBattleCheckBox);
        
        startPausedCheckBox = new JCheckBox("startPausedCheckBox", "Start Paused");
		startPausedCheckBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				//if(startPausedCheckBox.isSelected())
				//	showBattleCheckBox.setSelected(true);
			}
		});
		buttonPanel.add(startPausedCheckBox);
        
        controlArea.add(buttonPanel);
        // -------------
        controlArea.add(new JSeparator(JSeparator.HORIZONTAL));
        // ------------ Control panel
        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new FlowLayout());
        controlPanel.add(new JLabel("Survivor groups per session:"));
        
        // If total number of teams is less then four, make it the defauld number
		int numberOfGropus = Math.min(4,
            competition.getWarriorRepository().getNumberOfGroups());
		
        //warriorsPerGroupField = new JTextField(String.format("%d", numberOfGropus), 3); SHY-CHANGED
        //warriorsPerGroupField = new JTextField("warriorsPerGroupField", Integer.toString(numberOfGropus), 3);
		//controlPanel.add(warriorsPerGroupField);
		controlPanel.add(new JLabel("Sessions per groups combination:"));
		battlesPerGroupField = new JTextField("battlesPerGroupField", "100", 4);
		controlPanel.add(battlesPerGroupField);
		seed = new JTextField("seed", null, 4);
		seed.setText("guru");
		controlPanel.add(new JLabel("seed:"));
		controlPanel.add(seed);
		
		controlArea.add(controlPanel);
        
        // ------------
        getContentPane().add(controlArea, BorderLayout.SOUTH);

        showBattleRoom();

        m_codeEditor = new CodeEditor();
        m_playersPanel = new PlayersPanel(m_codeEditor);
        m_codeEditor.m_playersPanel = m_playersPanel;

        call_gwtStart();

        /*addWindowListener(new WindowListener() {
            public void windowOpened(WindowEvent e) {}
            public void windowClosing(WindowEvent e) {
                if (warThread!= null) {
                    competition.setAbort();
                }
            }

            public void windowClosed(WindowEvent e) {}
            public void windowIconified(WindowEvent e) {}
            public void windowDeiconified(WindowEvent e) {}
            public void windowActivated(WindowEvent e) {}
            public void windowDeactivated(WindowEvent e) {}
        });*/
    }

    public static native void call_gwtStart() /*-{
        $wnd.gwtStart();
    }-*/;

    public AnimationScheduler.AnimationCallback animCallback = new AnimationScheduler.AnimationCallback() {
        @Override
        public void execute(double timestamp) {
            try {
                boolean needMore = competition.continueRun(isBattleShown());
                if (needMore)
                    AnimationScheduler.get().requestAnimationFrame(animCallback);
			} catch (Exception e) {
				Console.log("continueRun EXCEPTION " + e.toString());
				e.printStackTrace();
			}
        }
    };

    public void requestFrame() {
        AnimationScheduler.get().requestAnimationFrame(animCallback);
    }
    
    /**
     * Starts a new war.
     * @return whether or not a new war was started.
     */
    public boolean runWar() {
        int battlesPerGroup = 0;
        try {
        	competition.setSeed(seed.getText().hashCode());
            battlesPerGroup = Integer.parseInt(battlesPerGroupField.getText().trim());
          /*  final int warriorsPerGroup = Integer.parseInt(
                warriorsPerGroupField.getText().trim());*/
        } catch (NumberFormatException e2) {
            JOptionPane.showMessageDialog(this, "Error in configuration");
        }
        /*    if (competition.getWarriorRepository().getNumberOfGroups() < warriorsPerGroup) {
                JOptionPane.showMessageDialog(this,
                    "Not enough survivors (got " +
                    competition.getWarriorRepository().getNumberOfGroups() +
                    " but " + warriorsPerGroup + " are needed)");
                return false;
            }*/

        // having numItems and groupSize allows having 4 players and running competitions of just any 3 of them
        // this is hardly useful in reality so I just set it to the same size

        if (battlesPerGroup == 0) {
            Console.error("battles per session needs to be more than 0");
            return false;
        }

        WarriorRepository repo = competition.getWarriorRepository();
        if (!repo.readWarriorFilesFromUI( m_playersPanel.getFiles(), m_playersPanel.getZombies() ))
            return false;
        columnGraph.clear(repo.getGroupNames());

        int numOfGroups = repo.getNumberOfGroups();
        if (numOfGroups == 0) {
            Console.error("can't run without any warriors"); // TBD-ERRMSG
            return false;
        }
        try {
            boolean needMore = competition.runCompetition(battlesPerGroup, numOfGroups, startPausedCheckBox.isSelected(), isBattleShown());
            if (needMore) {
                requestFrame();
                return true;
            }
        } catch (Exception e) {
            Console.log("runWar EXCEPTION " + e.toString());
            e.printStackTrace(Console.stream());
        }


        return false;
    }

    public void scoreChanged(String name, float addedValue, int groupIndex, int subIndex) {
        columnGraph.addToValue(groupIndex, subIndex, addedValue);
    }

    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == runWarButton) {
        	showBattleFrameIfNeeded();
            if (runWar()) {
            	competitionRunning = true;
				runWarButton.setEnabled(false);
            }
        }
    }


    public void onWarStart() {
    	showBattleFrameIfNeeded();
    }

    private void showBattleFrameIfNeeded() {
    	//if (showBattleCheckBox.isSelected() && battleFrame == null ) {
    		//showBattleRoom();
    		//showBattleCheckBox.setSelected(false);
    	//}
    }
    
    private void showBattleRoom() {
        competition.setSpeed(5);
        battleFrame = new WarFrame(competition, this);
       /* battleFrame.addWindowListener(new WindowListener() {
            public void windowOpened(WindowEvent e) {
            }

            public void windowClosing(WindowEvent e) {
            }

            public void windowClosed(WindowEvent e) {
                //System.out.println("BattleFrame=null");
                battleFrame = null;
                competition.setSpeed(Competition.MAXIMUM_SPEED);
            }

            public void windowIconified(WindowEvent e) {
            }

            public void windowDeiconified(WindowEvent e) {
            }

            public void windowActivated(WindowEvent e) {
            }

            public void windowDeactivated(WindowEvent e) {
            }
        });*/
        
        competition.addMemoryEventLister(battleFrame);
        competition.addCompetitionEventListener(battleFrame);
     /*   Rectangle battleFrameRect = new Rectangle(0, getY(), 750, 700);
        Rectangle screen = getGraphicsConfiguration().getBounds(); //for multiple monitors
           
        if (getX() + getWidth() <= screen.getX() + screen.getWidth()
        		- battleFrameRect.width)
        {
        	battleFrameRect.x = getX() + getWidth();
        }
        else if (screen.getX() + screen.getWidth() - battleFrameRect.width
        	- getWidth() >= screen.getX())
        {
        	setLocation((int) (screen.getX() + screen.getWidth() - battleFrameRect.width
        			- getWidth()), getY());
        	battleFrameRect.x = getX() + getWidth();
        }
        else
        {
        	setLocation((int)screen.getX(), getY());
        	battleFrameRect.x = getWidth();
        }
               
        battleFrame.setBounds(battleFrameRect);
        battleFrame.setVisible(true);
        */
    }

    public void onWarEnd(int reason, String winners) {
        warCounter++;
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                warCounterDisplay.setText("Sessions so far:" + warCounter +
                    " (out of " + totalWars + ")");
            };
        });
    }

    public void onRound(int round) {
    }

    public void onWarriorBirth(String warriorName) {
    }

    public void onWarriorDeath(String warriorName, String reason) {
    }

    public void onCompetitionStart() {
        warCounter = 0;
        totalWars = competition.getTotalNumberOfWars();
		this.runWarButton.setEnabled(false);
    }

    public void onCompetitionEnd() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                warCounterDisplay.setText("The competition is over. " +
                    warCounter + " sessions were run.");
            };
        });
        //warThread = null;
		this.runWarButton.setEnabled(true);
        runWarButton.setEnabled(true);
		competitionRunning = false;
    }
    
	@Override
	public void onEndRound() {
	}
	
}