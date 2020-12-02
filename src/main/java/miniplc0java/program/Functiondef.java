package miniplc0java.program;

import miniplc0java.analyser.SymbolEntry;
import miniplc0java.instruction.Instruction;

import java.util.ArrayList;
import java.util.List;

public class Functiondef {
    public String name;
    public List<SymbolEntry> params = new ArrayList<>();
    public int returnSize;
    public int localSize;
    public int bodySize;
    public ArrayList<Instruction> instructions = new ArrayList<>();

    @Override
    public String toString() {
        String res = "\n\tname='" + name + '\'' +
                "\tparams=" + params.size() +
                "\treturnSize=" + returnSize +
                "\tlocalSize=" + localSize +
                "\tbodySize=" + bodySize ;
        for (Instruction ins : instructions) {
            res += "\t\t"+ins.toString()+"\n";
        }
        return res;
    }
}
