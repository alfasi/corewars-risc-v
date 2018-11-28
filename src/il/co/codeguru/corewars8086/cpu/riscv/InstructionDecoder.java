package il.co.codeguru.corewars8086.cpu.riscv;

import il.co.codeguru.corewars8086.cpu.exceptions.CpuException;
import il.co.codeguru.corewars8086.cpu.exceptions.InvalidOpcodeException;
import il.co.codeguru.corewars8086.cpu.riscv.instruction_formats.*;
import il.co.codeguru.corewars8086.memory.MemoryException;
import il.co.codeguru.corewars8086.memory.RealModeMemory;
import il.co.codeguru.corewars8086.utils.Logger;

class InstructionDecoder {

    private InstructionRunner runner;

    public InstructionDecoder(InstructionRunner runner)
    {
        this.runner = runner;
    }

    public void decode_and_run(CpuStateRiscV state, InstructionBase i, RealModeMemory memory) throws MemoryException, CpuException
    {
        switch(i.getOpcode())
        {
            case 0x03:
                loadOpcode(state, i, memory);
                break;
            case 0x13:
                InstructionI ii = new InstructionI(i);
                switch (ii.getFunct3())
                {
                    case 0x0:
                        runner.addi(ii, state);
                        break;
                    case 0x1:
                        runner.slli(ii, state);
                        break;
                    case 0x2:
                        runner.slti(ii, state);
                        break;
                    case 0x3:
                        runner.sltiu(ii,state);
                        break;
                    case 0x4:
                        runner.xori(ii, state);
                        break;
                    case 0x5:
                        int imm = ii.getImmediate() >> 5;
                        switch(imm)
                        {
                            case 0:
                                runner.srli(ii, state);
                                break;
                            case 32:
                                runner.srai(ii, state);
                                break;
                            default:
                                throw new InvalidOpcodeException();
                        }
                        break;
                    case 0x6:
                        runner.ori(ii, state);
                        break;
                    case 0x7:
                        runner.andi(ii, state);
                        break;
                    default:
                        throw new InvalidOpcodeException();
                }
                break;
            case 0x17:
                runner.auipc(new InstructionU(i), state);
                break;
            case 0x23:
                InstructionS is = new InstructionS(i);
                switch(is.getFunct3())
                {
                    case 0:
                        runner.sb(is, state, memory);
                        break;
                    case 1:
                        runner.sh(is, state, memory);
                        break;
                    case 2:
                        runner.sw(is, state, memory);
                        break;
                    default:
                        throw new InvalidOpcodeException();
                }
                break;
            case 0x33:
                InstructionR ir = new InstructionR(i);
                switch(ir.getFunct3())
                {
                    case 0:
                        switch (ir.getFunct7())
                        {
                            case 0:
                                runner.add(ir,state);
                                break;
                            case 32:
                                runner.sub(ir,state);
                                break;
                            default:
                                throw new InvalidOpcodeException();
                        }
                        break;
                    case 1:
                        runner.sll(ir, state);
                        break;
                    case 2:
                        runner.slt(ir, state);
                        break;
                    case 3:
                        runner.sltu(ir,state);
                        break;
                    case 4:
                        runner.xor(ir, state);
                        break;
                    case 5:
                        switch(ir.getFunct7())
                        {
                            case 0:
                                runner.srl(ir, state);
                                break;
                            case 32:
                                runner.sra(ir, state);
                                break;
                            default:
                                throw new InvalidOpcodeException();
                        }
                        break;
                    case 6:
                        runner.or(ir, state);
                        break;
                    case 7:
                        runner.and(ir, state);
                        break;
                    default:
                        throw new InvalidOpcodeException();
                }
                break;
            case 0x37:
                runner.lui(new InstructionU(i), state);
                break;
            case 0x63:
                InstructionSB sb = new InstructionSB(i);
                switch(sb.getFunct3())
                {
                    case 0:
                        runner.beq(sb, state);
                        break;
                    case 1:
                        runner.bne(sb, state);
                        break;
                    case 4:
                        runner.blt(sb, state);
                        break;
                    case 5:
                        runner.bge(sb, state);
                        break;
                    case 6:
                        runner.bltu(sb, state);
                        break;
                    case 7:
                        runner.bgeu(sb, state);
                        break;
                    default:
                        throw new InvalidOpcodeException();
                }
                break;
            case 0x67:
                runner.jalr(new InstructionI(i), state);
                break;
            case 0x6f:
                runner.jal(new InstructionUJ(i), state);
                break;
            default:
                throw new InvalidOpcodeException();
        }
    }

    private void loadOpcode(CpuStateRiscV state, InstructionBase i, RealModeMemory memory) throws MemoryException, InvalidOpcodeException {
        InstructionI ii = new InstructionI(i);
        switch(ii.getFunct3())
        {
            case 0x0:
                runner.lb(ii, state, memory);
                break;
            case 0x1:
                runner.lh(ii, state, memory);
                break;
            case 0x2:
                runner.lw(ii, state, memory);
                break;
            case 0x4:
                runner.lbu(ii, state, memory);
                break;
            case 0x5:
                runner.lhu(ii, state, memory);
                break;
            default:
                throw new InvalidOpcodeException();
        }
    }
}
