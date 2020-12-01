package miniplc0java.analyser;

import miniplc0java.instruction.Instruction;

import java.util.ArrayList;
import java.util.List;

public class FunEntry {
    public String name;
    public List<SymbolEntry> params = new ArrayList<>();
    public int returnSize;
    public int bodySize;
    public ArrayList<Instruction> instructions = new ArrayList<>();
}
