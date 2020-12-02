package miniplc0java.program;

import miniplc0java.instruction.Instruction;

import java.util.ArrayList;
import java.util.List;

public class Program {
    private String magic = "00000000";
    private String version = "00000000";
    List<Globaldef> globaldefList = new ArrayList<>();
    List<Functiondef> functiondefList = new ArrayList<>();

    public void addFunc(Functiondef functiondef){
        functiondefList.add(functiondef);
    }

    public void addGlobal(Globaldef globaldef){
        globaldefList.add(globaldef);
    }

    @Override
    public String toString() {
        return "globList=" + globaldefList +
                "\nfuncList=" + functiondefList ;
    }

    public String toByteString(){
        String str = "begin\n";
        str += magic + "\n" + version + "\n";
        str += String.format("%08x\n",globaldefList.size());
        for(Globaldef globaldef:globaldefList){
            str += String.format("%02x\n%08x\n%s\n",
                    globaldef.isConst?1:0,globaldef.value.length()/2,globaldef.value);
        }
        str += String.format("%08x\n",functiondefList.size());
        for(Functiondef fun:functiondefList){
            str += String.format("%s\n%08x\n%08x\n%08x\n%08x\n",
                    fun.name,fun.returnSize,fun.params.size(),fun.localSize,fun.bodySize);
            for(Instruction ins : fun.instructions){
                str += ins.toByteString() + "\n";
            }
        }
        str += "\nend";
        return str;
    }
}
