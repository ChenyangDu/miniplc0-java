package miniplc0java.instruction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Instruction {
    private Operation opt;
    Integer x;

    public Instruction(Operation opt) {
        this.opt = opt;
        this.x = 0;
    }

    public Instruction(Operation opt, Integer x) {
        this.opt = opt;
        this.x = x;
    }

    public Instruction() {
        //this.opt = Operation.LIT;
        this.x = 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Instruction that = (Instruction) o;
        return opt == that.opt && Objects.equals(x, that.x);
    }

    @Override
    public int hashCode() {
        return Objects.hash(opt, x);
    }

    public Operation getOpt() {
        return opt;
    }

    public void setOpt(Operation opt) {
        this.opt = opt;
    }

    public Integer getX() {
        return x;
    }

    public void setX(Integer x) {
        this.x = x;
    }

    @Override
    public String toString() {
        if(haveParam()){
            return this.opt.toString() + " " + x;
        }
        return this.opt.toString();
    }

    public String toByteString(){
        String res = "";
        res += String.format("%02x",getOptByte());

        if(haveParam()){
            if(opt == Operation.PUSH){
                res += String.format("%016x",x);
            }else{
                res += String.format("%08x",x);
            }
        }
        return  res;
    }

    private boolean haveParam(){
        int opyByte = getOptByte();
        boolean haParam = false;
        switch (this.opt){
            case PUSH:
            case POPN:
            case LOCA:
            case ARGA:
            case GLOBA:
            case STACK_ALLOC:
            case BR:
            case BR_FALSE:
            case BR_TRUE:
            case CALL:
                haParam = true;
        }
        return haParam;
    }
    private int getOptByte(){
        int offset = 0;
        if(this.opt.ordinal() >= Operation.LOCA.ordinal()){
            offset = 5;
        }
        if(this.opt.ordinal() >= Operation.LOAD8.ordinal()){
            offset = 0x10-8;
        }
        if(this.opt.ordinal() >= Operation.ADD_I.ordinal()){
            offset = 0x20-18;
        }
        if(this.opt.ordinal() >= Operation.BR.ordinal()){
            offset = 0x41-43;
        }
        if(this.opt.ordinal() >= Operation.CALL.ordinal()){
            offset = 0x48 - Operation.CALL.ordinal();
        }
        if(this.opt.ordinal() >= Operation.SCAN_I.ordinal()){
            offset = 0x50 - Operation.SCAN_I.ordinal();
        }
        if(this.opt.ordinal() >= Operation.PRINT_I.ordinal()){
            offset = 0x50 - Operation.PRINT_I.ordinal();
        }
        if(this.opt.ordinal() >= Operation.PANIC.ordinal()){
            offset = 0x50 - Operation.PANIC.ordinal();
        }
        return this.opt.ordinal() + offset;
    }
}
