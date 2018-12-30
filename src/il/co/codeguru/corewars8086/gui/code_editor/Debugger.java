package il.co.codeguru.corewars8086.gui.code_editor;

import elemental2.dom.DomGlobal;
import elemental2.dom.Element;
import elemental2.dom.EventListener;
import elemental2.dom.HTMLElement;
import il.co.codeguru.corewars8086.cpu.riscv.CpuStateRiscV;
import il.co.codeguru.corewars8086.cpu.riscv.Memory;
import il.co.codeguru.corewars8086.gui.PlayersPanel;
import il.co.codeguru.corewars8086.gui.widgets.Console;
import il.co.codeguru.corewars8086.jsadd.Format;
import il.co.codeguru.corewars8086.memory.MemoryEventListener;
import il.co.codeguru.corewars8086.memory.RealModeAddress;
import il.co.codeguru.corewars8086.utils.disassembler.DisassemblerRiscV;
import il.co.codeguru.corewars8086.utils.disassembler.IDisassembler;
import il.co.codeguru.corewars8086.war.War;
import il.co.codeguru.corewars8086.war.Warrior;

import static il.co.codeguru.corewars8086.gui.code_editor.CodeEditor.*;
import static il.co.codeguru.corewars8086.memory.RealModeAddress.PARAGRAPH_SIZE;
import static il.co.codeguru.corewars8086.war.War.ARENA_SEGMENT;

public class Debugger {
    private final CodeEditor codeEditor;

    private HTMLElement m_lastDbgElement;
    private boolean m_lastIsAlive = false;
    private Memory m_mem = null;
    private DebuggerMemoryListener memoryListener = new DebuggerMemoryListener();

    private DbgLine[] m_singleByte = new DbgLine[256]; // hold lines with db XXh for memory write events
    private DbgLine[] m_dbglines;  // for every address, the line of display in the debugger panel or null if line is not displayed
    private DbgLine m_fillCmd;
    private PlayersPanel.Breakpoint[] m_dbgBreakpoints; // for every address, reference to a Breakpoint object if one exists
    private final EventListener m_dbgBrClickHandler = event -> {
        Element e = (Element) event.target;
        toggleBreakpointDbg(Integer.parseInt(e.innerHTML, 16));
    };
    private int m_atScrollP1 = -1, m_atScrollP2 = -1;


    private int m_lastDbgAddr = -1; // for knowing if we need to move it
    private int m_lastDbgAddrEnd = -1; // end (one after last) of the debugged Opcode (for edit handling)

    public static class DbgLine {
        // one such object can appear in multiple addresses for instance the comment int3 line
        public static final int FLAG_UNPARSED = 1;  // means this is a DbgLine of a value written by a warrior and not yet parsed by the disassembler
        public static final int FLAG_DEFINE_CODE = 2; // line that came from the user typed text that defines a number (db 123)
        public static final int FLAG_HAS_COMMENT = 4; // This DbgLine has comment lines after the first code line so when highlighting this line, need to highlight dfXXXXX instead of dXXXXX
        public static final int FLAG_LSTLINE_MAX = 0x7ff;
        public static final int FLAG_LSTLINE_SHIFT = 16;
        public static final int FLAG_LSTLINE = 0x07ff0000; // 5 - upper 12 bits of the flat is a 1-based line number of the LstLine that created this DbgLine or 0 if there isn't one
        public static final int FLAG_PLAYER_NUM_SHIFT = 27;
        public static final int FLAG_PLAYER_NUM = 0xf8000000; // upper 5 bits is the player number, valid only if there is a non-zero LstLine
        String text; // includes the command and any comment lines after it
        int flags = 0;
    }

    public Debugger(CodeEditor codeEditor) {
        this.codeEditor = codeEditor;
        m_dbglines = new DbgLine[War.ARENA_SIZE];
    }

    public Memory getMemory() {
        return m_mem;
    }
    
    public MemoryEventListener getMemoryListener() { return memoryListener;}

    public void setMemory(Memory memory) {
        m_mem = memory;
    }

    DbgLine getFillCmd() {
        return m_fillCmd;
    }

    DbgLine getDbgLine(int index) {
        return m_dbglines[index];
    }

    PlayersPanel.Breakpoint getDbgBreakpoint(int index) {
        return m_dbgBreakpoints[index];
    }

    public void setDebugMode(boolean debugMode)
    {
        if(debugMode)
        {
            // defer scrolling since we want to do this only after all sizes are correct and everything shown
            scrollToCodeInEditor(true);
        }
    }

    public void updateDebugLine() {
        // the first call to this is before debugMode is started to set the first debug line.
        // in this case we don't want to disassemble since the dbglines have not even been inited yet. sort of a hack.
        Warrior currentWarrior = getCurrentWarrior();
        if (currentWarrior == null)
            return;
        final int ipInsideArena = getWarrirorIp(currentWarrior);
        final boolean isAlive = currentWarrior.isAlive();

        CodeEditor.scrollToAddr(ipInsideArena, false); // make sure to scroll to it even the current line marker is on it
        if (ipInsideArena == m_lastDbgAddr && isAlive == m_lastIsAlive) {
            return; // nothing to do, the line is what we want it to be
        }
        if (m_lastDbgElement != null) // remove the last thing we put there
            m_lastDbgElement.classList.remove(m_lastIsAlive ? "current_dbg" : "current_dbg_dead");

        // the first call to this is before debugMode is started to set the first debug line.
        // in this case we don't want to disassemble since the dbglines have not even been inited yet. sort of a hack.
        if (m_dbglines[ipInsideArena] == null) {
            // got to a null line, means this address is hidden and is part of a preceding Opcode, first find that
            int opcodeAddr = ipInsideArena;
            while (m_dbglines[opcodeAddr] == null)
                --opcodeAddr;
            // fill the size of this Opcode with db lines,
            // do this before disassembly of the IP line to make sure we've erased the old Opcode correctly
            do {
                setByteFromMem(opcodeAddr);
                ++opcodeAddr;
            } while (m_dbglines[opcodeAddr] == null);
            // disassemble may eat at any of the db's after it, and might also each Opcode after that
            disassembleAddress(ipInsideArena + CODE_ARENA_OFFSET, ipInsideArena);
        } else {
            int flags = m_dbglines[ipInsideArena].flags;
            if ((flags & DbgLine.FLAG_UNPARSED) != 0 || (flags & DbgLine.FLAG_DEFINE_CODE) != 0) {
                disassembleAddress(ipInsideArena + CODE_ARENA_OFFSET, ipInsideArena);
            }
        }

        String ider = "d";
        if ((m_dbglines[ipInsideArena].flags & DbgLine.FLAG_HAS_COMMENT) != 0)
            ider = "df"; // a line with a comment after, don't highlight the entire line, just the first line. df is assured to exist if we have this flag

        HTMLElement dline = (HTMLElement) DomGlobal.document.getElementById(ider + Integer.toString(ipInsideArena));
        dline.classList.add(isAlive ? "current_dbg" : "current_dbg_dead");
        m_lastDbgElement = dline;
        this.m_lastDbgAddr = ipInsideArena;
        this.m_lastDbgAddrEnd = m_lastDbgAddr + 1;
        m_lastIsAlive = isAlive;
        while (m_lastDbgAddrEnd < (ARENA_SEGMENT * PARAGRAPH_SIZE) && m_dbglines[m_lastDbgAddrEnd] == null)
            this.m_lastDbgAddrEnd = m_lastDbgAddrEnd + 1;

    }

    private void disassembleAddress(int absaddr, int addrInArea) {
        byte[] memory_bytes = m_mem.getMemory();
        IDisassembler dis = new DisassemblerRiscV(memory_bytes, absaddr, m_mem.length());
        String text;
        try {
            text = dis.nextOpcode();
        } catch (IDisassembler.DisassemblerException e) {
            return;
        }
        eraseOpcode(addrInArea); // for example replacing at the start of a long db "ABC"
        int len = dis.lastOpcodeSize();

        DbgLine opline = new DbgLine();
        StringBuilder bs = new StringBuilder();
        for (int i = 0; i < len; ++i) {
            bs.append(Format.hex2(m_mem.loadByte(absaddr + i - (ARENA_SEGMENT * PARAGRAPH_SIZE)) & 0xff));
            bs.append(SPACE_FOR_HEX);
        }

        opline.text = "<span class='dbg_opcodes'>" + bs.toString() + "</span>" + text;
        m_dbglines[addrInArea] = opline;
        renderLineIfInView(addrInArea, opline);
        for (int i = 1; i < len; ++i) {
            // remove the lines of the bytes after it
            // don't know what opcodes I'm writing so need to make sure it remains consistent
            eraseOpcode(addrInArea + i);
            // this erases one line and possible adds db in the lines after it, this is simple good although it can write several times in the same place
        }
    }

    // erase the Opcode in addr, and take care to setByte the bytes after it that are affected
    private void eraseOpcode(int addrInArea) {
        m_dbglines[addrInArea] = null;
        renderLineIfInView(addrInArea, null);
        ++addrInArea;
        while (m_dbglines[addrInArea] == null) {
            setByteFromMem(addrInArea);
            ++addrInArea;
        }
    }

    private void setByteFromMem(int addrInArea) {
        int value = m_mem.loadByte(addrInArea);
        setByte(addrInArea, (byte) (value & 0xff));
    }

    public void setByte(int address, byte value) {
        DbgLine dbgline = getSingleByteLine(value);
        m_dbglines[address] = dbgline;
        renderLineIfInView(address, dbgline);
    }

    private void renderLineIfInView(int address, DbgLine dbgline) {
        int page = address / codeEditor.PAGE_SIZE;
        if (page == m_atScrollP1 || page == m_atScrollP2) {
            renderLine(address, dbgline);
        }
    }

    private DbgLine getSingleByteLine(byte bval) {
        int val = bval & 0xff; // to unsigned int
        DbgLine byteline = m_singleByte[val];
        if (byteline == null) {
            byteline = new DbgLine();
            String hexVal = Format.hex2(val);
            byteline.text = "<span class='dbg_opcodes'>" + hexVal + "</span>db " + hexVal + "h";
            byteline.flags = DbgLine.FLAG_UNPARSED;
        }
        m_singleByte[val] = byteline;
        return byteline;
    }

    public void initDebugAreaLines() {
        War war = codeEditor.getCurrentCompetition().getCurrentWar();

        if (m_fillCmd == null) {
            m_fillCmd = new DbgLine();
            m_fillCmd.text = "<span class='dbg_backfill'><span class='dbg_opcodes'>CC</span>int 3</span>";
        }

        for (int addr = 0; addr < War.ARENA_SIZE; ++addr) {
            m_dbglines[addr] = m_fillCmd;
        }
        for (CodeEditor.PageInfo p : codeEditor.getPages())
            p.isDirty = true;

        m_dbgBreakpoints = new PlayersPanel.Breakpoint[War.ARENA_SIZE];

        for (int i = 0; i < war.getNumWarriors(); ++i) {
            Warrior w = war.getWarrior(i);
            int playerLoadOffset = w.getLoadOffsetInt(); // in the area segment

            PlayersPanel.Code code = codeEditor.getPlayerPanel().findCode(w.getLabel());

            // transfer breakpoints
            assert code.lines != null : "unexpected null lines for code " + code.label + " of player" + code.player.getName();
            for (CodeEditor.LstLine lstline : code.lines)
                lstline.tmp_br = null;
            for (PlayersPanel.Breakpoint br : code.breakpoints) {
                assert br.lineNum - 1 < code.lines.size() : "unexpected lineNum in breakpoint";
                code.lines.get(br.lineNum - 1).tmp_br = br;
            }


            DbgLine last_dbgline = null;

            // comment or label on the first line, need to belong to the address before first
            if (code.lines.get(0).address == -1) {
                int befFirst = playerLoadOffset - 1;
                if (m_dbglines[befFirst] != null) {
                    last_dbgline = m_dbglines[befFirst];
                    if (last_dbgline == m_fillCmd) { // it's the shared DbgLine from above, make a copy of it since we don't want to change it
                        DbgLine copy = new DbgLine();
                        copy.text = last_dbgline.text;
                        last_dbgline = copy;
                        m_dbglines[befFirst] = last_dbgline;
                    }
                } else {
                    last_dbgline = new DbgLine();
                    last_dbgline.text = "";
                    m_dbglines[befFirst] = last_dbgline;
                }
            }

            for (int lsti = 0; lsti < code.lines.size(); ++lsti) {
                CodeEditor.LstLine lstline = code.lines.get(lsti);
                if (lstline.address == -1) {
                    assert last_dbgline != null : "Unexpected blank prev line";
                    last_dbgline.flags |= DbgLine.FLAG_HAS_COMMENT;
                    last_dbgline.text += "</div><div class='dbg_comment_line'>      <span class='dbg_opcodes'></span>" + lstline.code + "</div>";
                } else {
                    int loadAddr = lstline.address + playerLoadOffset;
                    DbgLine dbgline = new DbgLine();
                    String opcode = lstline.opcode;

                    dbgline.text = "<span class='dbg_opcodes'>" + opcode + "</span>" + lstline.code;
                    if (codeEditor.isDefineCode(lstline.code))
                        dbgline.flags = DbgLine.FLAG_DEFINE_CODE;

                    if (lsti <= DbgLine.FLAG_LSTLINE_MAX) {// lines above 2^16 are not tracked... should not come to this but just to be safe
                        dbgline.flags |= ((lsti + 1) << DbgLine.FLAG_LSTLINE_SHIFT);
                        dbgline.flags |= (i << DbgLine.FLAG_PLAYER_NUM_SHIFT);
                    }
                    m_dbglines[loadAddr] = dbgline;

                    last_dbgline = dbgline;

                    for (int j = 1; j < lstline.opcodesCount; ++j) {
                        m_dbglines[loadAddr + j] = null;
                    }

                    if (lstline.tmp_br != null)
                        m_dbgBreakpoints[loadAddr] = lstline.tmp_br;

                }
            }
        }
    }


    // called when an address is clicked to add a breakpoint for a line
    // all breakpoints of all players are visible and active
    // breakpoints in the debug view that are in addresses that don't correspond to code lines are transient.
    // they disappear once the debug session is over. They are not saved in the players m_breakpoints since it's unknown what players are they of
    public void toggleBreakpointDbg(int addr) {
        PlayersPanel.Breakpoint br = null;
        boolean wasAdded = false;

        if (m_dbgBreakpoints[addr] == null) {
            br = new PlayersPanel.Breakpoint(-1);
            m_dbgBreakpoints[addr] = br;
            wasAdded = true;
        } else {
            br = m_dbgBreakpoints[addr];
            m_dbgBreakpoints[addr] = null;
            wasAdded = false;
        }

        War war = codeEditor.getCurrentCompetition().getCurrentWar();

        DbgLine dbgline = getDbgLine(addr);
        int lsti = (dbgline.flags & DbgLine.FLAG_LSTLINE) >> DbgLine.FLAG_LSTLINE_SHIFT;
        if (lsti >= 1) {
            int playeri = (dbgline.flags & DbgLine.FLAG_PLAYER_NUM) >> DbgLine.FLAG_PLAYER_NUM_SHIFT;
            Warrior warrior = war.getWarrior(playeri);

            PlayersPanel.Code codeObj = codeEditor.getPlayerPanel().findCode(warrior.getLabel());

            if (wasAdded) {
                br.lineNum = lsti;
                // check sanity that there isn't a breakpoint in this line
                for (PlayersPanel.Breakpoint exist_br : codeObj.breakpoints)
                    assert exist_br.lineNum != br.lineNum : "Breakpoint of this line already exists! " + Integer.toString(br.lineNum);
                codeObj.breakpoints.add(br);
            } else {
                boolean removed = codeObj.breakpoints.remove(br);
                if (!removed)
                    Console.error("removed a breakpoint that did not exist?");
            }

            // add it to the editor as well if needed so it will be visible there when debugging is done
            if (codeObj == codeEditor.getPlayerPanel().getCodeInEditor())
                codeEditor.setLineNumBreakpoint(lsti, wasAdded);
        }
        renderLine(addr, dbgline);
    }

    // dbgline should already be in m_dgblines
    // dXXXXX is the whole line, possible containing the following comment lines
    // dfXXXXX is just the first line that is not a comment - markable by debugger when stepping
    // daXXXXX is the address of the line (not preset in comment lines)
    public void renderLine(int addr, DbgLine dbgline) {
        String addrstr = Integer.toString(addr);
        HTMLElement dline = (HTMLElement) DomGlobal.document.getElementById("d" + addrstr);
        if (dbgline == null) {
            dline.style.display = "none";
            return;
        }

        String addrhex = Format.hex4(addr);
        if ((dbgline.flags & DbgLine.FLAG_HAS_COMMENT) != 0) // this div tag is closed inside dbgline.text before the comment starts
            dline.innerHTML = "<div id='df" + addrstr + "'><span id='da" + addrstr + "'>" + addrhex + "</span>  " + dbgline.text;
        else
            dline.innerHTML = "<span id='da" + addrstr + "'>" + addrhex + "</span>  " + dbgline.text;
        dline.removeAttribute("style");

        HTMLElement da = (HTMLElement) DomGlobal.document.getElementById("da" + addrstr);
        da.addEventListener("click", m_dbgBrClickHandler);

        // mark breakpoint?
        PlayersPanel.Breakpoint br = getDbgBreakpoint(addr);
        if (br != null) {
            setDbgAddrBreakpoint(addr, true);
        }
    }

    private void setDbgAddrBreakpoint(int addr, boolean v) {
        Element e = DomGlobal.document.getElementById("da" + Integer.toString(addr));
        if (v)
            e.classList.add("dbg_breakpoint");
        else
            e.classList.remove("dbg_breakpoint");
    }

    public void scrollToCodeInEditor(boolean defer) {
        int ipInsideArena = getCurrentWarriorIp();
        if (ipInsideArena == -1) // not in competition
            return;

        CodeEditor.scrollToAddr(ipInsideArena, defer);
    }

    private int getCurrentWarriorIp() {
        return getWarrirorIp(getCurrentWarrior());
    }

    public Warrior getCurrentWarrior() {
        War war = codeEditor.getCurrentCompetition().getCurrentWar();
        if (war == null)
            return null;
        String label = codeEditor.getPlayerPanel().getCodeInEditor().getLabel();
        return war.getWarriorByLabel(label);
    }

    public static int getWarrirorIp(Warrior w) {
        if (w == null)
            return -1;
        CpuStateRiscV state = w.getCpuState();

        short ip = (short) state.getPc();

        return RealModeAddress.linearAddress(ARENA_SEGMENT, ip) - CODE_ARENA_OFFSET;
    }

    // from javascript scroll of debug area
    public void j_renderIfDirty(int pagenum) {
        if (pagenum == -1)
            return;
        PageInfo page = codeEditor.getPages()[pagenum];
        if (!page.isDirty)
            return;
        for (int addr = page.startAddr; addr < page.endAddr; ++addr) {
            DbgLine dbgline = getDbgLine(addr);
            renderLine(addr, dbgline);
        }
        page.isDirty = false;
    }

    public void j_setScrollAt(int p1, int p2) {
        j_renderIfDirty(p1);
        j_renderIfDirty(p2);

        m_atScrollP1 = p1;
        m_atScrollP2 = p2;
    }

    public class DebuggerMemoryListener implements MemoryEventListener
    {
        private EWriteState m_memWriteState = MemoryEventListener.EWriteState.INIT;
        @Override
        public void onMemoryWrite(RealModeAddress address, byte value) {
            // don't rewrite lines if we're in the stage of putting warriors in memory
            if (m_memWriteState != EWriteState.RUN)
                return;
            int absAddr = address.getLinearAddress();
            if (absAddr < ARENA_SEGMENT * PARAGRAPH_SIZE || absAddr >= ARENA_SEGMENT * PARAGRAPH_SIZE + War.ARENA_SIZE)
                return;
            int ipInsideArena = absAddr - (ARENA_SEGMENT * PARAGRAPH_SIZE); // arena * paragraph
            final int cIpInsideArea = ipInsideArena;

            int page = ipInsideArena / codeEditor.PAGE_SIZE;
            if (page < 0 || page >= codeEditor.getPages().length)
                return;

            codeEditor.getPages()[page].isDirty = true;

            DbgLine existing = getDbgLine(ipInsideArena);

            if (existing == getFillCmd()) {
                setByte(ipInsideArena, value);
            }
            else  {
                // find where this Opcode starts
                while (getDbgLine(ipInsideArena) == null)
                    --ipInsideArena;

                do {
                    // rewriting only a single Opcode so its not possible to cross to a new Opcode which will need reparsing
                    setByte(ipInsideArena, getMemory().loadByte(ipInsideArena));
                    ++ipInsideArena;
                } while (ipInsideArena < (ARENA_SEGMENT * PARAGRAPH_SIZE)&& getDbgLine(ipInsideArena) == null);
            }

            // if we just edited the byte under the debugger, need to reparse it
            if (cIpInsideArea >= m_lastDbgAddr && cIpInsideArea < m_lastDbgAddrEnd) {
                // make it go inside the next function
                Debugger.this.m_lastDbgAddr = -1;
                updateDebugLine();
            }
        }

        @Override
        public void onWriteState(EWriteState state) {
            m_memWriteState = state;
        }
    }

}