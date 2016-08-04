/*
 * Copyright 2014 Steve Ash
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.steveash.jopenfst;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import com.github.steveash.jopenfst.semiring.LogSemiring;
import com.github.steveash.jopenfst.semiring.Semiring;
import com.github.steveash.jopenfst.utils.FstUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import javax.annotation.Nullable;

import static com.github.steveash.jopenfst.utils.FstUtils.symbolTableEffectiveCopy;
import static com.google.common.base.Preconditions.checkArgument;

/**
 * A mutable finite state transducer implementation.
 */
public class MutableFst implements Fst {

  public static MutableFst emptyWithCopyOfSymbols(Fst fst) {
    MutableFst copy = new MutableFst(fst.getSemiring(),
                                     symbolTableEffectiveCopy(fst.getInputSymbols()),
                                     symbolTableEffectiveCopy(fst.getOutputSymbols())
    );
    if (fst.isUsingStateSymbols()) {
      copy.useStateSymbols(symbolTableEffectiveCopy(fst.getStateSymbols()));
    }
    return copy;
  }

  public static MutableFst copyFrom(Fst fst) {
    MutableFst copy = emptyWithCopyOfSymbols(fst);
    // build up states
    for (int i = 0; i < fst.getStateCount(); i++) {
      State source = fst.getState(i);
      MutableState target = new MutableState(source.getNumArcs());
      target.setFinalWeight(source.getFinalWeight());
      copy.setState(i, target);
    }
    // build arcs now that we have target state refs
    for (int i = 0; i < fst.getStateCount(); i++) {
      State source = fst.getState(i);
      MutableState target = copy.getState(i);
      for (int j = 0; j < source.getNumArcs(); j++) {
        Arc sarc = source.getArc(j);
        MutableState nextTargetState = copy.getState(sarc.getNextState().getId());
        MutableArc tarc = new MutableArc(sarc.getIlabel(), sarc.getOlabel(), sarc.getWeight(), nextTargetState);
        target.addArc(tarc);
      }
    }
    MutableState newStart = copy.getState(fst.getStartState().getId());
    copy.setStart(newStart);
    return copy;
  }

  private final Semiring semiring;
  private final ArrayList<MutableState> states;
  private MutableState start;
  private WriteableSymbolTable inputSymbols;
  private WriteableSymbolTable outputSymbols;
  private WriteableSymbolTable stateSymbols;

  public MutableFst() {
    this(makeDefaultRing(), new MutableSymbolTable(), new MutableSymbolTable());
  }

  private static Semiring makeDefaultRing() {
    return new LogSemiring();
  }

  /**
   * Constructor specifying the initial capacity of the states ArrayList (this is an optimization used in various
   * operations)
   *
   * @param numStates the initial capacity
   */
  public MutableFst(int numStates) {
    this(new ArrayList<MutableState>(numStates), makeDefaultRing(), new MutableSymbolTable(), new MutableSymbolTable());
  }

  public MutableFst(int numStates, Semiring semiring) {
    this(new ArrayList<MutableState>(numStates), semiring, new MutableSymbolTable(), new MutableSymbolTable());
  }

  /**
   * Constructor specifying the fst's semiring
   *
   * @param s the fst's semiring
   */
  public MutableFst(Semiring s) {
    this(s, new MutableSymbolTable(), new MutableSymbolTable());
  }

  /**
   * Constructs a new MutableFst with the given semiring and mutable symbol tables; NOTE that these
   * symbol tables are being GIVEN to own by this MutableFst (transfer of ownership); tables should
   * not be SHARED by FSTs so if you want to make copies -- then do it yourself or set the tables
   * after construction via one of the applicable methods
   * @param semiring
   * @param inputSymbolsToOwn
   * @param outputSymbolsToOwn
   */
  public MutableFst(Semiring semiring, WriteableSymbolTable inputSymbolsToOwn, WriteableSymbolTable outputSymbolsToOwn) {
    this(Lists.<MutableState>newArrayList(), semiring, inputSymbolsToOwn, outputSymbolsToOwn);
  }

  public MutableFst(WriteableSymbolTable inputSymbolsToOwn, WriteableSymbolTable outputSymbolsToOwn) {
    this(Lists.<MutableState>newArrayList(), makeDefaultRing(), inputSymbolsToOwn, outputSymbolsToOwn);
  }

  protected MutableFst(ArrayList<MutableState> states, Semiring semiring, WriteableSymbolTable inputSymbols,
                       WriteableSymbolTable outputSymbols) {
    this.states = states;
    this.semiring = semiring;
    this.inputSymbols = inputSymbols;
    this.outputSymbols = outputSymbols;
  }

  @Nullable
  @Override
  public WriteableSymbolTable getStateSymbols() {
    return stateSymbols;
  }

  /**
   * This sets a state symbols table; this takes ownership of this so don't share symbol
   * tables
   */
  public void useStateSymbols(WriteableSymbolTable stateSymbolsToOwn) {
    this.stateSymbols = stateSymbolsToOwn;
  }

  public void useStateSymbols() {
    this.stateSymbols = new MutableSymbolTable();
  }

  /**
   * Get the initial states
   */
  @Override
  public MutableState getStartState() {
    return start;
  }

  /**
   * Get the semiring
   */
  @Override
  public Semiring getSemiring() {
    return semiring;
  }

  /**
   * Set the initial state
   *
   * @param start the initial state
   */
  public MutableState setStart(MutableState start) {
    checkArgument(start.getId() >= 0, "must set id before setting start");
    throwIfSymbolTableMissingId(start.getId());
    this.start = start;
    return start;
  }


  public MutableState newStartState() {
    return newStartState(null);
  }

  public MutableState newStartState(@Nullable String startStateSymbol) {
    checkArgument(start == null, "cant add more than one start state");
    MutableState newStart = newState(startStateSymbol);
    setStart(newStart);
    return newStart;
  }

  /**
   * Get the number of states in the fst
   */
  @Override
  public int getStateCount() {
    return this.states.size();
  }

  @Override
  public MutableState getState(int index) {
    return states.get(index);
  }

  @Override
  public MutableState getState(String name) {
    Preconditions.checkState(stateSymbols != null, "cant ask by name if not using state symbols");
    return getState(stateSymbols.get(name));
  }

  /**
   * Adds a state to the fst
   *
   * @param state the state to be added
   */
  public MutableState addState(MutableState state) {
    return addState(state, null);
  }

  public MutableState addState(MutableState state, @Nullable String newStateSymbol) {
    checkArgument(state.getId() == -1, "trying to add a state that already has id");
    this.states.add(state);
    state.id = states.size() - 1;
    if (stateSymbols != null) {
      Preconditions.checkNotNull(newStateSymbol, "if using symbol table for states everything must have "
                                                 + "a symbol");
      stateSymbols.put(newStateSymbol, state.id);
    } else {
      Preconditions.checkState(newStateSymbol == null, "cant pass state name if not using symbol table");
    }
    return state;
  }

  public MutableState setState(int id, MutableState state) {
    checkArgument(state.getId() == -1, "trying to add a state that already has id");
    state.setId(id);
    throwIfSymbolTableMissingId(id);
    // they provided the id so index properly
    if (id >= this.states.size()) {
      this.states.ensureCapacity(id + 1);
      for (int i = states.size(); i <= id; i++) {
        this.states.add(null);
      }
    }
    Preconditions.checkState(this.states.get(id) == null, "cant write two states with ", id);
    this.states.set(id, state);
    return state;
  }

  public MutableState newState() {
    return newState(null);
  }

  public MutableState newState(@Nullable String newStateSymbol) {
    MutableState s = new MutableState();
    return addState(s, newStateSymbol);
  }

  public MutableState getOrNewState(String stateSymbol) {
    Preconditions.checkNotNull(stateSymbols, "cant use this without state symbols");
    if (stateSymbols.contains(stateSymbol)) {
      return getState(stateSymbol);
    }
    return newState(stateSymbol);
  }

  /**
   * Adds a new arc in the FST between startStateSymbol and endStateSymbol with inSymbol and outSymbol
   * and edge weight; if the state symbols or in/out symbols dont exist then they will be added
   * @param startStateSymbol
   * @param inSymbol
   * @param outSymbol
   * @param endStateSymbol
   * @param weight
   * @return
   */
  public MutableArc addArc(String startStateSymbol, String inSymbol, String outSymbol, String endStateSymbol, double weight) {
    Preconditions.checkNotNull(stateSymbols, "cant use this without state symbols; call useStateSymbols()");
    return addArc(
        getOrNewState(startStateSymbol),
        inSymbol,
        outSymbol,
        getOrNewState(endStateSymbol),
        weight
    );
  }

  public MutableArc addArc(MutableState startState, String inSymbol, String outSymbol, MutableState endState, double weight) {
    checkArgument(this.states.get(startState.getId()) == startState, "cant pass state that doesnt exist in fst");
    checkArgument(this.states.get(endState.getId()) == endState, "cant pass end state that doesnt exist in fst");
    MutableArc newArc = new MutableArc(inputSymbols.getOrAdd(inSymbol),
                                    outputSymbols.getOrAdd(outSymbol),
                                    weight,
                                    endState
    );
    startState.addArc(newArc);
    return newArc;
  }

  @Override
  public WriteableSymbolTable getInputSymbols() {
    return inputSymbols;
  }

  @Override
  public WriteableSymbolTable getOutputSymbols() {
    return outputSymbols;
  }

  @Override
  public int getInputSymbolCount() {
    return inputSymbols.size();
  }

  @Override
  public int getOutputSymbolCount() {
    return outputSymbols.size();
  }

  public void setInputSymbolsAsCopyFrom(Fst sourceInputSymbols) {
    this.inputSymbols = symbolTableEffectiveCopy(sourceInputSymbols.getInputSymbols());
  }

  public void setInputSymbolsAsCopyFromThatOutput(Fst that) {
    this.inputSymbols = symbolTableEffectiveCopy(that.getOutputSymbols());
  }

  public void setOutputSymbolsAsCopyFrom(Fst sourceOutputSymbols) {
    this.outputSymbols = symbolTableEffectiveCopy(sourceOutputSymbols.getOutputSymbols());
  }

  public void setOutputSymbolsAsCopyFromThatInput(Fst that) {
    this.outputSymbols = symbolTableEffectiveCopy(that.getInputSymbols());
  }

  public void setOutputSymbolsAsCopy(SymbolTable copyFrom) {
    this.outputSymbols = symbolTableEffectiveCopy(copyFrom);
  }

  public void setInputSymbolsAsCopy(SymbolTable copyFrom) {
    this.inputSymbols = symbolTableEffectiveCopy(copyFrom);
  }

  @Override
  public int lookupInputSymbol(String symbol) {
    return inputSymbols.get(symbol);
  }

  @Override
  public int lookupOutputSymbol(String symbol) {
    return outputSymbols.get(symbol);
  }

  @Override
  public void throwIfThisOutputIsNotThatInput(Fst that) {
    if (!FstUtils.symbolTableEquals(outputSymbols, that.getInputSymbols())) {
      throw new IllegalArgumentException("Symbol tables don't match, cant compose " + this + " to " + that);
    }
  }

  @Override
  public boolean isUsingStateSymbols() {
    return stateSymbols != null;
  }

  @Override
  public void throwIfInvalid() {
    Preconditions.checkNotNull(semiring, "must have a semiring");
    Preconditions.checkNotNull(start, "must have a start state");
  }

  @Deprecated // just use the text version; it will be more forward compatible
  public void saveModel(File file) throws IOException {
    FstInputOutput.saveModel(this, file);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Fst(start=").append(start).append(", isyms=").append(inputSymbols).append(", osyms=").append(
        outputSymbols).append(", semiring=").append(semiring).append(")\n");
    for (State s : states) {
      sb.append("  ").append(s).append("\n");
      int numArcs = s.getNumArcs();
      for (int j = 0; j < numArcs; j++) {
        Arc a = s.getArc(j);
        sb.append("    ").append(a).append("\n");
      }
    }
    return sb.toString();
  }

  /**
   * Deletes the given states and remaps the existing state ids
   */
  public void deleteStates(Collection<? extends State> statesToDelete) {
    for (State state : statesToDelete) {
      deleteState(state);
    }
    remapStateIds();
  }

  /**
   * Deletes a state;
   *
   * @param state the state to delete
   */
  private void deleteState(State state) {
    if (state.getId() == this.start.getId()) {
      throw new IllegalArgumentException("Cannot delete start state.");
    }

    this.states.remove(state);

    // delete arc's with nextstate equal to stateid
    ArrayList<Integer> toDelete;
    int numStates = states.size();
    for (int i = 0; i < numStates; i++) {
      MutableState s1 = states.get(i);

      toDelete = new ArrayList<>();
      int numArcs = s1.getNumArcs();
      for (int j = 0; j < numArcs; j++) {
        Arc a = s1.getArc(j);
        if (a.getNextState().equals(state)) {
          toDelete.add(j);
        }
      }
      // indices not change when deleting in reverse ordering
      Object[] toDeleteArray = toDelete.toArray();
      Arrays.sort(toDeleteArray);
      for (int j = toDelete.size() - 1; j >= 0; j--) {
        Integer index = (Integer) toDeleteArray[j];
        s1.deleteArc(index);
      }
    }
  }

  private void remapStateIds() {
    int numStates = states.size();
    for (int i = 0; i < numStates; i++) {
      states.get(i).id = i;
    }
  }

  public void throwIfAnyNullStates() {
    for (int i = 0; i < states.size(); i++) {
      if (states.get(i) == null) {
        throw new IllegalStateException("Cannot have a null state in an FST. State " + i);
      }
    }
  }

  private void throwIfSymbolTableMissingId(int id) {
    if (stateSymbols != null && !stateSymbols.invert().containsKey(id)) {
      throw new IllegalArgumentException("If you're using a state symbol table then every state "
                                         + "must be in the state symbol table");
    }
  }

  @Override
  public boolean equals(Object o) {
    return FstUtils.fstEquals(this, o);
  }

  @Override
  public int hashCode() {
    int result = semiring != null ? semiring.hashCode() : 0;
    result = 31 * result + (states != null ? states.hashCode() : 0);
    result = 31 * result + (start != null ? start.hashCode() : 0);
    result = 31 * result + (inputSymbols != null ? inputSymbols.hashCode() : 0);
    result = 31 * result + (outputSymbols != null ? outputSymbols.hashCode() : 0);
    return result;
  }
}
