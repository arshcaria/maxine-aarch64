/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.sun.max.ins;

import java.io.*;

import javax.swing.*;

import com.sun.max.collect.*;
import com.sun.max.ins.debug.*;
import com.sun.max.ins.gui.*;
import com.sun.max.ins.java.*;
import com.sun.max.ins.memory.*;
import com.sun.max.ins.method.*;
import com.sun.max.ins.type.*;
import com.sun.max.memory.*;
import com.sun.max.program.*;
import com.sun.max.tele.*;
import com.sun.max.tele.debug.*;
import com.sun.max.tele.method.*;
import com.sun.max.tele.object.*;
import com.sun.max.unsafe.*;
import com.sun.max.util.*;
import com.sun.max.vm.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.bytecode.*;
import com.sun.max.vm.classfile.constant.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.reference.*;
import com.sun.max.vm.run.*;
import com.sun.max.vm.thread.*;
import com.sun.max.vm.type.*;
import com.sun.max.vm.value.*;


/**
 * Provider of {@link InspectorAction}s that are of general use.
 * <p>
 * <b>How to create an {@link InspectorAction} to perform "doSomething":</b>
 *
 * <ol>
 *
 * <li><b>Create an action class:</b>
 * <ul>
 * <li> <code>final class DoSometingAction extends {@link InspectorAction}</code></li>
 * <li> The Action classes are in package scope so that they can be used by {@link InspectorKeyBindings}.</li>
 * <li> Add a title:  <code>private static final DEFAULT_NAME = "do something"</code>.</li>
 * <li> If the
 * action is interactive, for example if it produces a dialog, then the name should conclude with "...".
 * Capitalize the first word of the title but not the others, except for distinguished names such as
 * "Inspector" and acronyms.</li>
 * <li> For singletons, add a package scope constructor with one argument:  <code>String title</code></li>
 * <li> For non-singletons, package scope constructor contains additional arguments that
 * customize the action, for example that specify to what "something" is to be done.</li>
 * <li> In the constructor: <code>super(inspection(), title == null ? DEFAULT_TITLE : title);</code>
 * (being able to override isn't used in many cases, but it adds flexibility).</li>
 * <li> If a singleton and if it contains state, for example enabled/disabled, that might change
 * depending on external circumstances, then register for general notification:
 * <code>_refreshableActions.append(this);</code> in the constructor.</li>
 * <li> Alternately, if state updates depend on a more specific kind of event, register
 * in the constructor explicitly for that event with a listener, for example
 * <code>focus().addListener(new InspectionFocusAdapter() { .... });</code>
 * The body of the listener should call <code>refresh</code>.</li>
 * <li>Override <code>protected void procedure()</code> with a method that does what
 * needs to be done.</li>
 * <li>If a singleton and if it contains state that might be changed depending on
 * external circumstances, override <code>public void refresh(boolean force)</code>
 * with a method that updates the state.</li>
 * </ul></li>
 *
 *<li><b>Create a singleton variable if needed</b>:
 *<ul>
 * <li>If the command is a singleton, create an initialized variable, static if possible.</li>
 * <li><code>private static InspectorAction _doSomething = new DoSomethingAction(null);</code></li>
 * </ul></li>
 *
 * <li><b>Create an accessor:</b>
 * <ul>
 * <li>Singleton: <code>public InspectorAction doSomething()</code>.</li>
 * <li> Singleton accessor returns the singleton variable.</li>
 * <li>Non-singletons have additional arguments that customize the action, e.g. specifying to what "something"
 * is to be done; they also take a <code>String title</code> argument that permits customization of the
 * action's name, for example when it appears in menus.</li>
 * <li> Non-singletons return <code>new DoSomethignAction(args, String title)</code>.</li>
 * <li>Add a descriptive Javadoc comment:  "@return an Action that does something".</li>
 * </ul></li>
 *
 * </ol>
 * <p>
 * @author Bernd Mathiske
 * @author Michael Van De Vanter
 * @author Aritra Bandyopadhyay
 */
public class InspectionActions extends AbstractInspectionHolder implements Prober{

    private static final int TRACE_VALUE = 2;

    /**
     * Name of the Action for searching in an Inspector view.
     */
    public static final String SEARCH_ACTION = "Search";

    /**
     * Actions that are held and shared; they have state that will be refreshed.
     * This is particularly important for actions that enable/disable, depending on the inspection state.
     */
    private final AppendableSequence<InspectorAction> _refreshableActions = new ArrayListSequence<InspectorAction>();

    InspectionActions(Inspection inspection) {
        super(inspection);
        Trace.line(TRACE_VALUE, "InspectionActions initialized.");
    }

    public final void refresh(boolean force) {
        for (Prober prober : _refreshableActions) {
            prober.refresh(force);
        }
    }

    public final void redisplay() {
        // non-op
    }

    /**
     * Action:  displays the {@link AboutDialog}.
     */
    final class AboutAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "About";

        AboutAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
        }

        @Override
        protected void procedure() {
            new AboutDialog(inspection());
        }
    }

    private InspectorAction _about = new AboutAction(null);

    /**
     * @return an Action that will display the {@link AboutDialog}.
     */
    public final InspectorAction about() {
        return _about;
    }


    /**
     * Action:  displays the {@link PreferenceDialog}.
     */
    final class PreferencesAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Preferences";

        PreferencesAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
        }

        @Override
        protected void procedure() {
            new PreferenceDialog(inspection());
        }
    }

    private InspectorAction _preferences = new PreferencesAction(null);

    /**
     * @return an Action that will display the {@link PreferenceDialog}.
     */
    public final InspectorAction preferences() {
        return _preferences;
    }


    /**
     * Action:  refreshes all data from the VM.
     */
    final class RefreshAllAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Refresh all views";

        RefreshAllAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
        }

        @Override
        protected void procedure() {
            inspection().refreshAll(true);
        }
    }

    private final InspectorAction _refreshAll = new RefreshAllAction(null);

    /**
     * @return an Action that updates all displayed information read from the VM.
     */
    public final InspectorAction refreshAll() {
        return _refreshAll;
    }

    /**
     * Action:  close all open inspectors that match a predicate.
     */
    final class CloseViewsAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Close views";

        private final Predicate<Inspector> _predicate;

        CloseViewsAction(Predicate<Inspector> predicate, String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _predicate = predicate;
        }

        @Override
        protected void procedure() {
            gui().removeInspectors(_predicate);
        }
    }

    /**
     * @param predicate a predicate that returns true for all Inspectors to be closed.
     * @param title a string name for the Action, uses default name if null.
     * @return an Action that will close views.
     */
    public final InspectorAction closeViews(Predicate<Inspector> predicate, String title) {
        return new CloseViewsAction(predicate, title);
    }


    /**
     * Action:  closes all open inspectors.
     */
    final class CloseAllViewsAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Close all views";

        CloseAllViewsAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
        }

        @Override
        protected void procedure() {
            gui().removeInspectors(Predicate.Static.alwaysTrue(Inspector.class));
        }
    }

    private final InspectorAction _closeAll = new CloseAllViewsAction(null);

    /**
     * @return an Action that closes all open inspectors.
     */
    public final InspectorAction closeAllViews() {
        return _closeAll;
    }


    /**
     * Action:  quits inspector.
     */
    final class QuitAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Quit Inspector";

        QuitAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
        }

        @Override
        protected void procedure() {
            inspection().quit();
        }
    }

    private final InspectorAction _quit = new QuitAction(null);

    /**
     * @return an Action that quits the VM inspection session.
     */
    public final InspectorAction quit() {
        return _quit;
    }


    /**
     * Action:  relocates the boot image, assuming that the inspector was invoked
     * with the option {@link MaxineInspector#suspendingBeforeRelocating()} set.
     */
    final class RelocateBootImageAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Relocate Boot Image";

        RelocateBootImageAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
        }

        @Override
        protected void procedure() {
            try {
                maxVM().advanceToJavaEntryPoint();
            } catch (IOException ioException) {
                gui().errorMessage("error during relocation of boot image");
            }
            setEnabled(false);
        }
    }

    private final InspectorAction _relocateBootImage = new RelocateBootImageAction(null);

    /**
     * @return an Action that relocates the boot image, assuming that the inspector was invoked
     * with the option {@link MaxineInspector#suspendingBeforeRelocating()} set.
     */
    public final InspectorAction relocateBootImage() {
        return _relocateBootImage;
    }


    /**
     * Action:  sets level of trace output in inspector code.
     */
    final class SetInspectorTraceLevelAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Set Inspector trace level...";

        SetInspectorTraceLevelAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
        }

        @Override
        protected void procedure() {
            final int oldLevel = Trace.level();
            int newLevel = oldLevel;
            final String input = gui().inputDialog(DEFAULT_TITLE, Integer.toString(oldLevel));
            try {
                newLevel = Integer.parseInt(input);
            } catch (NumberFormatException numberFormatException) {
                gui().errorMessage(numberFormatException.toString());
            }
            if (newLevel != oldLevel) {
                Trace.on(newLevel);
            }
        }
    }

    private final InspectorAction _setInspectorTraceLevel = new SetInspectorTraceLevelAction(null);

    /**
     * @return an interactive Action that permits setting the level of inspector {@link Trace} output.
     */
    public final InspectorAction setInspectorTraceLevel() {
        return _setInspectorTraceLevel;
    }


    /**
     * Action:  changes the threshold determining when the Inspectors uses its
     * {@linkplain InspectorInterpeter interpreter} for access to VM state.
     */
    final class ChangeInterpreterUseLevelAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Change Interpreter use level...";

        ChangeInterpreterUseLevelAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            final int oldLevel = maxVM().getInterpreterUseLevel();
            int newLevel = oldLevel;
            final String input = gui().inputDialog("Change interpreter use level (0=none, 1=some, etc)", Integer.toString(oldLevel));
            try {
                newLevel = Integer.parseInt(input);
            } catch (NumberFormatException numberFormatException) {
                gui().errorMessage(numberFormatException.toString());
            }
            if (newLevel != oldLevel) {
                maxVM().setInterpreterUseLevel(newLevel);
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess());
        }
    }

    private final InspectorAction _changeInterpreterUseLevel = new ChangeInterpreterUseLevelAction(null);

    /**
     * @return an interactive action that permits changing the level at which the interpreter
     * will be used when communicating with the VM.
     */
    public final InspectorAction changeInterpreterUseLevel() {
        return _changeInterpreterUseLevel;
    }


    /**
     * Action:  sets debugging level for transport.
     * Appears unused October '08 (mlvdv)
     */
    final class SetTransportDebugLevelAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Set transport debug level...";

        SetTransportDebugLevelAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            final int oldLevel = maxVM().transportDebugLevel();
            int newLevel = oldLevel;
            final String input = gui().inputDialog(" (Set transport debug level, 0=none, 1=some, etc)", Integer.toString(oldLevel));
            try {
                newLevel = Integer.parseInt(input);
            } catch (NumberFormatException numberFormatException) {
                gui().errorMessage(numberFormatException.toString());
            }
            if (newLevel != oldLevel) {
                maxVM().setTransportDebugLevel(newLevel);
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess());
        }
    }

    private final InspectorAction _setTransportDebugLevel = new SetTransportDebugLevelAction(null);

    /**
     * @return an interactive action that permits setting the debugging level for transport.
     */
    public final InspectorAction setTransportDebugLevel() {
        return _setTransportDebugLevel;
    }


    /**
     * Action: runs Inspector commands from a specified file.
     */
    final class RunFileCommandsAction extends InspectorAction {

        RunFileCommandsAction() {
            super(inspection(), "Execute commands from file...");
        }

        @Override
        protected void procedure() {
            final String fileName = gui().inputDialog("File name: ", FileCommands.defaultCommandFile());
            if (fileName != null && !fileName.equals("")) {
                maxVM().executeCommandsFromFile(fileName);
            }
        }
    }

    private final InspectorAction _runFileCommands = new RunFileCommandsAction();

    /**
     * @return an interactive Action that will run Inspector commands from a specified file.
     */
    public final InspectorAction runFileCommands() {
        return _runFileCommands;
    }


    /**
     * Action:  updates the {@linkplain MaxVM#updateLoadableTypeDescriptorsFromClasspath() types available} on
     * the VM's class path by rescanning the complete class path for types.
     */
    final class UpdateClasspathTypesAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Rescan class path for types";

        UpdateClasspathTypesAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
        }

        @Override
        protected void procedure() {
            maxVM().updateLoadableTypeDescriptorsFromClasspath();
        }
    }

    private final InspectorAction _updateClasspathTypes = new UpdateClasspathTypesAction(null);

    /**
     * @return an Action that updates the {@linkplain MaxVM#updateLoadableTypeDescriptorsFromClasspath() types available} on
     * the VM's class path by rescanning the complete class path for types.
     */
    public final InspectorAction updateClasspathTypes() {
        return _updateClasspathTypes;
    }


    /**
     * Action: sets the level of tracing in the VM interactively.
     */
    final class SetVMTraceLevelAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Set VM trace level";

        SetVMTraceLevelAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            final int oldLevel = maxVM().getVMTraceLevel();
            int newLevel = oldLevel;
            final String input = gui().inputDialog("Set VM Trace Level", Integer.toString(oldLevel));
            try {
                newLevel = Integer.parseInt(input);
            } catch (NumberFormatException numberFormatException) {
                gui().errorMessage(numberFormatException.toString());
            }
            if (newLevel != oldLevel) {
                maxVM().setVMTraceLevel(newLevel);
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess());
        }
    }

    private InspectorAction _setVMTraceLevel = new SetVMTraceLevelAction(null);

    /**
     * @return an interactive Action that will set the level of tracing in the VM.
     */
    public final InspectorAction setVMTraceLevel() {
        return _setVMTraceLevel;
    }


    /**
     * Action: sets the threshold of tracing in the VM interactively.
     */
    final class SetVMTraceThresholdAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Set VM trace threshold";

        SetVMTraceThresholdAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            final long oldThreshold = maxVM().getVMTraceThreshold();
            long newThreshold = oldThreshold;
            final String input = gui().inputDialog("Set VM trace threshold", Long.toString(oldThreshold));
            try {
                newThreshold = Long.parseLong(input);
            } catch (NumberFormatException numberFormatException) {
                gui().errorMessage(numberFormatException.toString());
            }
            if (newThreshold != oldThreshold) {
                maxVM().setVMTraceThreshold(newThreshold);
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess());
        }
    }

    private InspectorAction _setVMTraceThreshold = new SetVMTraceThresholdAction(null);

    /**
     * @return an interactive Action that will set the threshold of tracing in the VM.
     */
    public final InspectorAction setVMTraceThreshold() {
        return _setVMTraceThreshold;
    }


    /**
     * Action:  makes visible and highlights the {@link BootImageInspector}.
     */
    final class ViewBootImageAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Boot image info";

        ViewBootImageAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
        }

        @Override
        protected void procedure() {
            BootImageInspector.make(inspection()).highlight();
        }
    }

    private InspectorAction _viewBootImage = new ViewBootImageAction(null);

    /**
     * @return an Action that will make visible the {@link BootImageInspector}.
     */
    public final InspectorAction viewBootImage() {
        return _viewBootImage;
    }


    /**
     * Action:  makes visible and highlights the {@link BreakpointsInspector}.
     */
    final class ViewBreakpointsAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Breakpoints";

        ViewBreakpointsAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            BreakpointsInspector.make(inspection()).highlight();
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess());
        }
    }

    private InspectorAction _viewBreakpoints = new ViewBreakpointsAction(null);

    /**
     * @return an Action that will make visible the {@link BreakpointsInspector}.
     */
    public final InspectorAction viewBreakpoints() {
        return _viewBreakpoints;
    }


    /**
     * Action:  makes visible and highlights the {@link MemoryRegionsInspector}.
     */
    final class ViewMemoryRegionsAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Memory regions";

        ViewMemoryRegionsAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            MemoryRegionsInspector.make(inspection()).highlight();
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess());
        }
    }

    private InspectorAction _viewMemoryRegions = new ViewMemoryRegionsAction(null);

    /**
     * @return an Action that will make visible the {@link MemoryRegionsInspector}.
     */
    public final InspectorAction viewMemoryRegions() {
        return _viewMemoryRegions;
    }


    /**
     * Action:  makes visible the {@link MethodInspector}.
     */
    final class ViewMethodCodeAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Method code";

        ViewMethodCodeAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
        }

        @Override
        protected void procedure() {
            final TeleCodeLocation teleCodeLocation = maxVM().createCodeLocation(focus().thread().instructionPointer());
            focus().setCodeLocation(teleCodeLocation, true);
        }
    }

    private InspectorAction _viewMethodCode = new ViewMethodCodeAction(null);

    /**
     * @return an Action that will make visible the {@link MethodInspector}, with
     * initial view set to the method containing the instruction pointer of the current thread.
     */
    public final InspectorAction viewMethodCode() {
        return _viewMethodCode;
    }


    /**
     * Action:  makes visible and highlights the {@link RegistersInspector}.
     */
    final class ViewRegistersAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Registers";

        ViewRegistersAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            RegistersInspector.make(inspection()).highlight();
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess() && focus().hasThread());
        }
    }

    private InspectorAction _viewRegisters = new ViewRegistersAction(null);

    /**
     * @return an Action that will make visible the {@link RegistersInspector}.
     */
    public final InspectorAction viewRegisters() {
        return _viewRegisters;
    }


    /**
     * Action:  makes visible and highlights the {@link StackInspector}.
     */
    final class ViewStackAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Stack";

        ViewStackAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            StackInspector.make(inspection()).highlight();
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess() && focus().hasThread());
        }
    }

    private InspectorAction _viewStack = new ViewStackAction(null);

    /**
     * @return an Action that will make visible the {@link StackInspector}.
     */
    public final InspectorAction viewStack() {
        return _viewStack;
    }


    /**
     * Action:  makes visible and highlights the {@link ThreadsInspector}.
     */
    final class ViewThreadsAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Threads";

        ViewThreadsAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            ThreadsInspector.make(inspection()).highlight();
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess());
        }
    }

    private InspectorAction _viewThreads = new ViewThreadsAction(null);

    /**
     * @return an Action that will make visible the {@link ThreadsInspector}.
     */
    public final InspectorAction viewThreads() {
        return _viewThreads;
    }


    /**
     * Action:  makes visible and highlights the {@link ThreadLocalsInspector}.
     */
    final class ViewVmThreadLocalsAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "VM thread locals";

        ViewVmThreadLocalsAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            ThreadLocalsInspector.make(inspection()).highlight();
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess() && focus().hasThread());
        }
    }

    private InspectorAction _viewVmThreadLocals = new ViewVmThreadLocalsAction(null);

    /**
     * @return an Action that will make visible the {@link ThreadsInspector}.
     */
    public final InspectorAction viewVmThreadLocals() {
        return _viewVmThreadLocals;
    }


    /**
     * Action:  copies a hex string version of a {@link Word} to the system clipboard.
     */
    final class CopyWordAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Copy word to clipboard";
        private final Word _word;

        private CopyWordAction(Word word, String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _word = word;
        }

        @Override
        public void procedure() {
            gui().postToClipboard(_word.toHexString());
        }
    }

    /**
     * @param a {@link Word} from the VM.
     * @param title a string to use as the title of the action, uses default name if null.
     * @return an Action that copies the word's text value in hex to the system clipboard
     */
    public final InspectorAction copyWord(Word word, String title) {
        return new CopyWordAction(word, title);
    }

    /**
     * @param a {@link Word} wrapped as a {@link Value} from the VM.
     * @param title a string to use as the title of the action, uses default name if null.
     * @return an Action that copies the word's text value in hex to the system clipboard,
     * null if not a word.
     */
    public final InspectorAction copyValue(Value value, String title) {
        Word word = Word.zero();
        try {
            word = value.asWord();
        } catch (Throwable throwable) {
        }
        final InspectorAction action = new CopyWordAction(word, title);
        if (word.isZero()) {
            action.setEnabled(false);
        }
        return action;
    }


    /**
     * Action:  inspect a memory region, interactive if no location is specified.
     */
    final class InspectMemoryAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Inspect memory";
        private final Address _address;
        private final TeleObject _teleObject;

        InspectMemoryAction() {
            super(inspection(), "Inspect memory at address...");
            _address = null;
            _teleObject = null;
            _refreshableActions.append(this);
        }

        InspectMemoryAction(Address address, String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _address = address;
            _teleObject = null;
        }

        InspectMemoryAction(TeleObject teleObject, String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _address = null;
            _teleObject = teleObject;
        }

        @Override
        protected void procedure() {
            if (_teleObject != null) {
                MemoryInspector.create(inspection(), _teleObject).highlight();
            } else if (_address != null) {
                MemoryInspector.create(inspection(), _address).highlight();
            } else {
                new AddressInputDialog(inspection(), maxVM().bootImageStart(), "Inspect memory at address...", "Inspect") {
                    @Override
                    public void entered(Address address) {
                        MemoryInspector.create(inspection(), address).highlight();
                    }
                };
            }
        }
    }

    private final InspectorAction _inspectMemoryAction = new InspectMemoryAction();

    /**
     * @return an interactive Action that will create a Memory Inspector
     */
    public final InspectorAction inspectMemory() {
        return _inspectMemoryAction;
    }

    /**
     * @param teleObject surrogate for an object in the VM
     * @param title a string name for the Action, uses default name if null
     * @return an Action that will create a Memory Inspector at the address
     */
    public final InspectorAction inspectMemory(TeleObject teleObject, String title) {
        return new InspectMemoryAction(teleObject, title);
    }

    /**
     * @param address a valid memory {@link Address} in the VM
     * @param title a string name for the action, uses default name if null
     * @return an interactive Action that will create a Memory Inspector at the address
     */
    public final InspectorAction inspectMemory(Address address, String title) {
        return new InspectMemoryAction(address, title);
    }


    /**
     * Action: inspect memory starting at the beginning of the boot heap.
     */
    final class InspectBootHeapMemoryAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Inspect memory at boot heap start";

        InspectBootHeapMemoryAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
        }

        @Override
        protected void procedure() {
            MemoryInspector.create(inspection(), maxVM().bootImageStart());
        }
    }

    private final InspectorAction _inspectBootHeapMemory = new InspectBootHeapMemoryAction(null);

    /**
     * @return an Action that will create a Memory Inspector at the start of the boot heap
     */
    public final InspectorAction inspectBootHeapMemory() {
        return _inspectBootHeapMemory;
    }

    /**
     * Action: inspect memory starting at the beginning of the boot code region.
     */
    final class InspectBootCodeMemoryAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Inspect memory at boot code start";

        InspectBootCodeMemoryAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
        }

        @Override
        protected void procedure() {
            MemoryInspector.create(inspection(),  maxVM().teleBootCodeRegion().start());
        }
    }

    private final InspectorAction _inspectBootCodeMemory = new InspectBootCodeMemoryAction(null);

    /**
     * @return an Action that will create a Memory Inspector at the start of the boot code
     */
    public final InspectorAction inspectBootCodeMemory() {
        return _inspectBootCodeMemory;
    }


    /**
     * Action:   inspect a memory region as words, interactive if no location specified.
     */
    final class InspectMemoryWordsAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Inspect memory words";
        private final Address _address;
        private final TeleObject _teleObject;

        InspectMemoryWordsAction() {
            super(inspection(), "Inspect memory words at address...");
            _refreshableActions.append(this);
            _address = null;
            _teleObject = null;
        }

        InspectMemoryWordsAction(Address address, String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _address = address;
            _teleObject = null;
        }

        InspectMemoryWordsAction(TeleObject teleObject, String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _address = null;
            _teleObject = teleObject;
        }

        @Override
        protected void procedure() {
            if (_teleObject != null) {
                MemoryWordInspector.create(inspection(), _teleObject).highlight();
            }
            if (_address != null) {
                MemoryWordInspector.create(inspection(), _address).highlight();
            } else {
                new AddressInputDialog(inspection(), maxVM().bootImageStart(), "Inspect memory words at address...", "Inspect") {

                    @Override
                    public void entered(Address address) {
                        MemoryWordInspector.create(inspection(), address).highlight();
                    }
                };
            }
        }
    }

    private final InspectorAction _inspectMemoryWordsAction = new InspectMemoryWordsAction();

    /**
     * @return an interactive Action that will create a MemoryWords Inspector
     */
    public final InspectorAction inspectMemoryWords() {
        return _inspectMemoryWordsAction;
    }

    /**
     * @param teleObject a surrogate for a valid object in the VM
     * @param title a name for the action
     * @return an Action that will create a Memory Words Inspector at the address
     */
    public final InspectorAction inspectMemoryWords(TeleObject teleObject, String title) {
        return new InspectMemoryWordsAction(teleObject, title);
    }

    /**
     * @param address a valid memory {@link Address} in the VM
     * @param title a name for the action
     * @return an Action that will create a Memory Words Inspector at the address
     */
    public final InspectorAction inspectMemoryWords(Address address, String title) {
        return new InspectMemoryWordsAction(address, title);
    }


    /**
     * Action: inspect memory starting at the beginning of the boot heap.
     */
    final class InspectBootHeapMemoryWordsAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Inspect memory words at boot heap start";

        InspectBootHeapMemoryWordsAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            MemoryWordInspector.create(inspection(), maxVM().bootImageStart()).highlight();
        }
    }

    private final InspectorAction _inspectBootHeapMemoryWords = new InspectBootHeapMemoryWordsAction(null);

    /**
     * @return an Action that will create a Memory Inspector at the start of the boot heap
     */
    public final InspectorAction inspectBootHeapMemoryWords() {
        return _inspectBootHeapMemoryWords;
    }


    /**
     * Action: inspect memory starting at the beginning of the boot code region.
     */
    final class InspectBootCodeMemoryWordsAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Inspect memory words at boot code start";

        InspectBootCodeMemoryWordsAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
        }

        @Override
        protected void procedure() {
            MemoryWordInspector.create(inspection(), maxVM().teleBootCodeRegion().start()).highlight();
        }
    }

    private final InspectorAction _inspectBootCodeMemoryWords = new InspectBootCodeMemoryWordsAction(null);

    /**
     * @return an Action that will create a Memory Inspector at the start of the boot code
     */
    public final InspectorAction inspectBootCodeMemoryWords() {
        return _inspectBootCodeMemoryWords;
    }


    /**
     * Action: sets inspection focus to specified {@link MemoryRegion}.
     */
    final class SelectMemoryRegionAction extends InspectorAction {

        private final MemoryRegion _memoryRegion;
        private static final String DEFAULT_TITLE = "Select memory region";

        SelectMemoryRegionAction(String title, MemoryRegion memoryRegion) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _memoryRegion = memoryRegion;
        }

        @Override
        protected void procedure() {
            focus().setMemoryRegion(_memoryRegion);
        }
    }

    /**
     * @return an Action that will create a Memory Inspector at the start of the boot code
     */
    public final InspectorAction selectMemoryRegion(MemoryRegion memoryRegion) {
        final String title = "Select Memory Region \"" + memoryRegion.description() + "\"";
        return new SelectMemoryRegionAction(title, memoryRegion);
    }


    /**
     * Action: create an Object Inspector, interactively specified by address..
     */
    final class InspectObjectAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Inspect heap object at address...";

        InspectObjectAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
        }

        @Override
        protected void procedure() {
            new AddressInputDialog(inspection(), maxVM().teleBootHeapRegion().start(), "Inspect heap object at address...", "Inspect") {

                @Override
                public void entered(Address address) {
                    final Pointer pointer = address.asPointer();
                    if (maxVM().isValidOrigin(pointer)) {
                        final Reference objectReference = maxVM().originToReference(pointer);
                        final TeleObject teleObject = maxVM().makeTeleObject(objectReference);
                        focus().setHeapObject(teleObject);
                    } else {
                        gui().errorMessage("heap object not found at 0x"  + address.toHexString());
                    }
                }
            };
        }
    }

    private final InspectorAction _inspectObject = new InspectObjectAction(null);

    /**
     * @return an Action that will create an Object Inspector interactively, prompting the user for a numeric object ID
     */
    public final InspectorAction inspectObject() {
        return _inspectObject;
    }


    /**
     * Action:  creates an inspector for a specific heap object in the VM.
     */
    final class InspectSpecifiedObjectAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Inspect object";
        final TeleObject _teleObject;

        InspectSpecifiedObjectAction(TeleObject teleObject, String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _teleObject = teleObject;
        }

        @Override
        protected void procedure() {
            focus().setHeapObject(_teleObject);
        }
    }

    /**
     * @param surrogate for a heap object in the VM.
     * @param title a string name for the Action, uses default name if null
     * @return an Action that will create an Object Inspector
     */
    public final InspectorAction inspectObject(TeleObject teleObject, String title) {
        return new InspectSpecifiedObjectAction(teleObject, title);
    }


    /**
     * Action: create an Object Inspector, interactively specified by the inspector's OID.
     */
    final class InspectObjectByIDAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Inspect heap object by ID...";

        InspectObjectByIDAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
        }

        @Override
        protected void procedure() {
            final String input = gui().inputDialog("Inspect heap object by ID..", "");
            try {
                final long oid = Long.parseLong(input);
                final TeleObject teleObject = maxVM().findObjectByOID(oid);
                if (teleObject != null) {
                    focus().setHeapObject(teleObject);
                } else {
                    gui().errorMessage("failed to find heap object for ID: " + input);
                }
            } catch (NumberFormatException numberFormatException) {
                gui().errorMessage("Not a ID: " + input);
            }
        }
    }

    private final InspectorAction _inspectObjectByID = new InspectObjectByIDAction(null);

    /**
     * @return an Action that will create an Object Inspector interactively, prompting the user for a numeric object ID
     */
    public final InspectorAction inspectObjectByID() {
        return _inspectObjectByID;
    }

    /**
     * Action: create an Object Inspector for the boot {@link ClassRegistry} in the VM.
     */
    final class InspectBootClassRegistryAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Inspect boot class registry";

        InspectBootClassRegistryAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
        }

        @Override
        protected void procedure() {
            final TeleObject teleBootClassRegistry = maxVM().makeTeleObject(maxVM().bootClassRegistryReference());
            focus().setHeapObject(teleBootClassRegistry);
        }
    }

    private final InspectorAction _inspectBootClassRegistry = new InspectBootClassRegistryAction(null);

    /**
     * @return an action that will create an Object Inspector for the boot {@link ClassRegistry} in the VM.
     */
    public final InspectorAction inspectBootClassRegistry() {
        return _inspectBootClassRegistry;
    }


    /**
     * Action:  inspect a {@link ClassActor} object for an interactively named class loaded in the VM,
     * specified by class name.
     */
    final class InspectClassActorByNameAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Inspect ClassActor by name...";

        InspectClassActorByNameAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
        }

        @Override
        protected void procedure() {
            final TeleClassActor teleClassActor = ClassActorSearchDialog.show(inspection(), "Inspect ClassActor ...", "Inspect");
            if (teleClassActor != null) {
                focus().setHeapObject(teleClassActor);
            }
        }
    }

    private final InspectorAction _inspectClassActorByName = new InspectClassActorByNameAction(null);

    /**
     * @return an interactive Action that inspects a {@link ClassActor} object for a class loaded in the VM,
     * specified by class name.
     */
    public final InspectorAction inspectClassActorByName() {
        return _inspectClassActorByName;
    }

    /**
     * Action:  inspect a {@link ClassActor} for an interactively named class loaded in the VM,
     * specified by the class ID in hex.
     */
    final class InspectClassActorByHexIdAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Inspect ClassActor by ID (Hex) ...";

        InspectClassActorByHexIdAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
        }

        @Override
        protected void procedure() {
            final String value = gui().questionMessage("ID (hex): ");
            if (value != null && !value.equals("")) {
                try {
                    final int serial = Integer.parseInt(value, 16);
                    final TeleClassActor teleClassActor = maxVM().findTeleClassActor(serial);
                    if (teleClassActor == null) {
                        gui().errorMessage("failed to find classActor for ID:  0x" + Integer.toHexString(serial));
                    } else {
                        focus().setHeapObject(teleClassActor);
                    }
                } catch (NumberFormatException ex) {
                    gui().errorMessage("Hex integer required");
                }
            }
        }
    }

    private final InspectorAction _inspectClassActorByHexId = new InspectClassActorByHexIdAction(null);

    /**
     * @return an interactive Action that inspects a {@link ClassActor} object for a class loaded in the VM,
     * specified by class ID in hex.
     */
    public final InspectorAction inspectClassActorByHexId() {
        return _inspectClassActorByHexId;
    }


    /**
     * Action:  inspect a {@link ClassActor} for an interactively named class loaded in the VM,
     * specified by the class ID in decimal.
     */
    final class InspectClassActorByDecimalIdAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Inspect ClassActor by ID (decimal) ...";
        InspectClassActorByDecimalIdAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
        }

        @Override
        protected void procedure() {
            final String value = gui().questionMessage("ID (decimal): ");
            if (value != null && !value.equals("")) {
                try {
                    final int serial = Integer.parseInt(value, 10);
                    final TeleClassActor teleClassActor = maxVM().findTeleClassActor(serial);
                    if (teleClassActor == null) {
                        gui().errorMessage("failed to find ClassActor for ID: " + serial);
                    } else {
                        focus().setHeapObject(teleClassActor);
                    }
                } catch (NumberFormatException ex) {
                    gui().errorMessage("Hex integer required");
                }
            }
        }
    }
    private final InspectorAction _inspectClassActorByDecimalId = new InspectClassActorByDecimalIdAction(null);

    /**
     * @return an interactive Action that inspects a {@link ClassActor} object for a class loaded in the VM,
     * specified by class ID in decimal.
     */
    public final InspectorAction inspectClassActorByDecimalId() {
        return _inspectClassActorByDecimalId;
    }


    /**
     * Action: visits a {@link MethodActor} object in the VM, specified by name.
     */
    final class InspectMethodActorByNameAction extends InspectorAction {

        private static final String DEFAULT_TITLE =  "Inspect MethodActor...";

        InspectMethodActorByNameAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
        }

        @Override
        protected void procedure() {
            final TeleClassActor teleClassActor = ClassActorSearchDialog.show(inspection(), "Inspect MethodActor in class...", "Select");
            if (teleClassActor != null) {
                final TeleMethodActor teleMethodActor = MethodActorSearchDialog.show(inspection(), teleClassActor, "Inspect MethodActor...", "Inspect");
                if (teleMethodActor != null) {
                    focus().setHeapObject(teleMethodActor);
                }
            }
        }

    }

    private InspectorAction _inspectMethodActorByName = new InspectMethodActorByNameAction(null);

    /**
     * @return an interactive Action that will visit a {@link MethodActor} object in the VM, specified by name.
     */
    public final InspectorAction inspectMethodActorByName() {
        return _inspectMethodActorByName;
    }


    /**
     * Action:  displays in the {@MethodInspector} the method whose target code contains
     * an interactively specified address.
     */
    final class ViewMethodCodeByAddressAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "View method code containing target code address...";

        public ViewMethodCodeByAddressAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
        }

        @Override
        protected void procedure() {
            new AddressInputDialog(inspection(), maxVM().bootImageStart(), "View method code containing target code address...", "View Code") {

                @Override
                public boolean isValidInput(Address address) {
                    return maxVM().makeTeleTargetMethod(address) != null;
                }

                @Override
                public void entered(Address address) {
                    focus().setCodeLocation(maxVM().createCodeLocation(address), false);
                }
            };
        }
    }

    private final InspectorAction _viewMethodCodeByAddress = new ViewMethodCodeByAddressAction(null);

    /**
     * @return an interactive action that displays in the {@link MethodInspector} the method whose
     * target code contains the specified address in the VM.
     */
    public final InspectorAction viewMethodCodeByAddress() {
        return _viewMethodCodeByAddress;
    }

    /**
     * Action:  displays in the {@MethodInspector} the method whose target code contains
     * an interactively specified address.
     */
    final class ViewRuntimeStubByAddressAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "View runtime stub code containing target code address...";

        public ViewRuntimeStubByAddressAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
        }

        @Override
        protected void procedure() {
            new AddressInputDialog(inspection(), maxVM().bootImageStart(), "View runtime stub code containing target code address...", "View Code") {

                @Override
                public boolean isValidInput(Address address) {
                    return maxVM().makeTeleRuntimeStub(address) != null;
                }

                @Override
                public void entered(Address address) {
                    focus().setCodeLocation(maxVM().createCodeLocation(address), false);
                }
            };
        }

    }

    private final InspectorAction _viewRuntimeStubByAddress = new ViewRuntimeStubByAddressAction(null);

    /**
     * @return an interactive action that displays in the {@link MethodInspector} the method whose
     * target code contains the specified address in the VM.
     */
    public final InspectorAction viewRuntimeStubByAddress() {
        return _viewRuntimeStubByAddress;
    }



    /**
     * Action:  displays in the {@MethodInspector} the method code containing the current code selection.
     */
    final class ViewMethodCodeAtSelectionAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "View method code at current selection";
        public ViewMethodCodeAtSelectionAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            final TeleCodeLocation teleCodeLocation = focus().codeLocation();
            focus().setCodeLocation(teleCodeLocation, true);
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(focus().hasCodeLocation());
        }
    }

    private final ViewMethodCodeAtSelectionAction _viewMethodCodeAtSelection = new ViewMethodCodeAtSelectionAction(null);

    /**
     * @return an action that displays in the {@link MethodInspector} the method code
     * containing the current code selection.
     */
    public final InspectorAction viewMethodCodeAtSelection() {
        return _viewMethodCodeAtSelection;
    }


    /**
     * Action:  displays in the {@MethodInspector} the method code containing the current instruction pointer.
     */
    final class ViewMethodCodeAtIPAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "View method code at IP";
        public ViewMethodCodeAtIPAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            final Pointer instructionPointer = focus().thread().instructionPointer();
            focus().setCodeLocation(maxVM().createCodeLocation(instructionPointer), true);
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(focus().hasThread());
        }
    }

    private final ViewMethodCodeAtIPAction _viewMethodCodeAtIP = new ViewMethodCodeAtIPAction(null);

    /**
     * @return an Action that displays in the {@link MethodInspector} the method
     * containing the current instruction pointer.
     */
    public final InspectorAction viewMethodCodeAtIP() {
        return _viewMethodCodeAtIP;
    }


    /**
     * Action:  displays in the {@MethodInspector} the bytecode for an interactively specified method.
     */
    final class ViewMethodBytecodeByNameAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "View method bytecode...";

        public ViewMethodBytecodeByNameAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
        }

        @Override
        protected void procedure() {
            final TeleClassActor teleClassActor = ClassActorSearchDialog.show(inspection(), "View bytecode for method in class...", "Select");
            if (teleClassActor != null) {
                final Predicate<TeleMethodActor> hasBytecodePredicate = new Predicate<TeleMethodActor>() {

                    @Override
                    public boolean evaluate(TeleMethodActor teleMethodActor) {
                        return teleMethodActor.hasCodeAttribute();
                    }
                };
                final TeleMethodActor teleMethodActor = MethodActorSearchDialog.show(inspection(), teleClassActor, hasBytecodePredicate, "View Bytecode for Method...", "Inspect");
                if (teleMethodActor != null && teleMethodActor instanceof TeleClassMethodActor) {
                    final TeleClassMethodActor teleClassMethodActor = (TeleClassMethodActor) teleMethodActor;
                    final TeleCodeLocation teleCodeLocation = maxVM().createCodeLocation(teleClassMethodActor, 0);
                    focus().setCodeLocation(teleCodeLocation, false);
                }
            }
        }
    }

    private final InspectorAction _viewMethodBytecodeByName = new ViewMethodBytecodeByNameAction(null);

    /**
     * @return an interactive Action that displays bytecode in the {@link MethodInspector}
     * for a selected method.
     */
    public final InspectorAction viewMethodBytecodeByName() {
        return _viewMethodBytecodeByName;
    }


    /**
     * Action:  displays in the {@MethodInspector} the target code for an interactively specified method.
     */
    final class ViewMethodTargetCodeByNameAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "View method target code...";

        public ViewMethodTargetCodeByNameAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
        }

        @Override
        protected void procedure() {
            final TeleClassActor teleClassActor = ClassActorSearchDialog.show(inspection(), "View target code for method in class...", "Select");
            if (teleClassActor != null) {
                final Sequence<TeleTargetMethod> teleTargetMethods = TargetMethodSearchDialog.show(inspection(), teleClassActor, "View Target Code for Method...", "View Code", false);
                if (teleTargetMethods != null) {
                    focus().setCodeLocation(maxVM().createCodeLocation(teleTargetMethods.first().callEntryPoint()), false);
                }
            }
        }
    }

    private final InspectorAction _viewMethodTargetCodeByName = new ViewMethodTargetCodeByNameAction(null);

    /**
     * @return an interactive Action that displays target code in the {@link MethodInspector}
     * for a selected method.
     */
    public final InspectorAction viewMethodTargetCodeByName() {
        return _viewMethodTargetCodeByName;
    }


    /**
     * Action:  displays in the {@link MethodInspector} the code of a specified
     * method in the boot image.
     */
    final class ViewMethodCodeInBootImageAction extends InspectorAction {

        private final int _offset;

        public ViewMethodCodeInBootImageAction(int offset, Class clazz, String name, Class... parameterTypes) {
            super(inspection(), clazz.getName() + "." + name + SignatureDescriptor.fromJava(Void.TYPE, parameterTypes).toJavaString(false, false));
            _offset = offset;
        }

        @Override
        protected void procedure() {
            focus().setCodeLocation(maxVM().createCodeLocation(maxVM().bootImageStart().plus(_offset)), true);
        }
    }

    private final InspectorAction _viewRunMethodCodeInBootImage =
        new ViewMethodCodeInBootImageAction(maxVM().bootImage().header()._vmRunMethodOffset, MaxineVM.class, "run", MaxineVM.runMethodParameterTypes());

    /**
     * @return an Action that displays in the {@link MethodInspector} the code of
     * the {@link MaxineVM#run()} method in the boot image.
     */
    public final InspectorAction viewRunMethodCodeInBootImage() {
        return _viewRunMethodCodeInBootImage;
    }

    private final InspectorAction _viewThreadRunMethodCodeInBootImage =
        new ViewMethodCodeInBootImageAction(maxVM().bootImage().header()._vmThreadRunMethodOffset, VmThread.class, "run", int.class, Address.class, Pointer.class,
                    Pointer.class, Pointer.class, Pointer.class, Pointer.class, Pointer.class, Pointer.class, Pointer.class);

    /**
     * @return an Action that displays in the {@link MethodInspector} the code of
     * the {@link VmThread#run()} method in the boot image.
     */
    public final InspectorAction viewThreadRunMethodCodeInBootImage() {
        return _viewThreadRunMethodCodeInBootImage;
    }

    private final InspectorAction _viewSchemeRunMethodCodeInBootImage =
        new ViewMethodCodeInBootImageAction(maxVM().bootImage().header()._runSchemeRunMethodOffset, maxVM().vmConfiguration().runPackage().schemeTypeToImplementation(RunScheme.class), "run");

    /**
     * @return an Action that displays in the {@link MethodInspector} the code of
     * the {@link RunScheme#run()} method in the boot image.
     */
    public final InspectorAction viewSchemeRunMethodCodeInBootImage() {
        return _viewSchemeRunMethodCodeInBootImage;
    }


    /**
     * Action:  displays in the {@MethodInspector} a body of native code whose location contains
     * an interactively specified address.
     */
    final class ViewNativeCodeByAddressAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "View native code containing  address...";

        public ViewNativeCodeByAddressAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
        }

        @Override
        protected void procedure() {
            // Most likely situation is that we are just about to call a native method in which case RAX is the address
            final TeleNativeThread teleNativeThread = focus().thread();
            assert teleNativeThread != null;
            final Address indirectCallAddress = teleNativeThread.integerRegisters().getCallRegisterValue();
            final Address initialAddress = indirectCallAddress == null ? maxVM().bootImageStart() : indirectCallAddress;
            new AddressInputDialog(inspection(), initialAddress, "View native code containing code address...", "View Code") {
                @Override
                public void entered(Address address) {
                    focus().setCodeLocation(maxVM().createCodeLocation(address), true);
                }
            };
        }
    }

    private final InspectorAction _viewNativeCodeByAddress = new ViewNativeCodeByAddressAction(null);

    /**
     * @return an interactive action that displays in the {@link MethodInspector} a body of native code whose
     * location contains the specified address in the VM.
     */
    public final InspectorAction viewNativeCodeByAddress() {
        return _viewNativeCodeByAddress;
    }


    /**
     * Action:  removes the breakpoint from the VM that is currently selected.
     */
    final class RemoveSelectedBreakpointAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Remove selected breakpoint";

        RemoveSelectedBreakpointAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _refreshableActions.append(this);
            focus().addListener(new InspectionFocusAdapter() {
                @Override
                public void breakpointFocusSet(TeleBreakpoint oldTeleBreakpoint, TeleBreakpoint teleBreakpoint) {
                    refresh(false);
                }
            });
        }

        @Override
        protected void procedure() {
            final TeleBreakpoint selectedTeleBreakpoint = focus().breakpoint();
            if (selectedTeleBreakpoint != null) {
                focus().setBreakpoint(null);
                selectedTeleBreakpoint.remove();
            } else {
                gui().errorMessage("No breakpoint selected");
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(focus().hasBreakpoint());
        }
    }

    private InspectorAction _removeBreakpoint = new RemoveSelectedBreakpointAction(null);

    /**
     * @return an Action that will remove the currently selected breakpoint, if any.
     */
    public final InspectorAction removeBreakpoint() {
        return _removeBreakpoint;
    }


    /**
     * Action: removes a specific  breakpoint in the VM.
     */
    final class RemoveBreakpointAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Remove breakpoint";

        final TeleBreakpoint _teleBreakpoint;

        RemoveBreakpointAction(TeleBreakpoint teleBreakpoint, String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _teleBreakpoint = teleBreakpoint;
        }

        @Override
        protected void procedure() {
            if (focus().breakpoint() == _teleBreakpoint) {
                focus().setBreakpoint(null);
            }
            _teleBreakpoint.remove();
        }
    }

    /**
     * @param surrogate for a breakpoint in the VM.
     * @param title a string name for the Action, uses default name if null.
     * @return an Action that will remove the breakpoint
     */
    public final InspectorAction removeBreakpoint(TeleBreakpoint teleBreakpoint, String title) {
        return new RemoveBreakpointAction(teleBreakpoint, title);
    }


    /**
     * Action: removes all existing breakpoints in the VM.
     */
    final class RemoveAllBreakpointsAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Remove all breakpoints";

        RemoveAllBreakpointsAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _refreshableActions.append(this);
            inspection().addInspectionListener(new InspectionListenerAdapter() {
                @Override
                public void breakpointSetChanged() {
                    refresh(true);
                }
            });
        }

        @Override
        protected void procedure() {
            focus().setBreakpoint(null);
            for (TeleTargetBreakpoint targetBreakpoint : maxVM().targetBreakpoints()) {
                targetBreakpoint.remove();
            }
            for (TeleBytecodeBreakpoint bytecodeBreakpoint : maxVM().bytecodeBreakpoints()) {
                bytecodeBreakpoint.remove();
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess() && (maxVM().bytecodeBreakpointCount() > 0  || maxVM().targetBreakpointCount() > 0));
        }
    }

    private InspectorAction _removeAllBreakpoints = new RemoveAllBreakpointsAction(null);

    /**
     * @return an Action that will remove all breakpoints in the VM.
     */
    public final InspectorAction removeAllBreakpoints() {
        return _removeAllBreakpoints;
    }


    /**
     * Action: enables a specific  breakpoint in the VM.
     */
    final class EnableBreakpointAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Enable breakpoint";
        final TeleBreakpoint _teleBreakpoint;

        EnableBreakpointAction(TeleBreakpoint teleBreakpoint, String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _teleBreakpoint = teleBreakpoint;
        }

        @Override
        protected void procedure() {
            if (focus().breakpoint() == _teleBreakpoint) {
                focus().setBreakpoint(null);
            }
            _teleBreakpoint.setEnabled(true);
            inspection().refreshAll(false);
        }


        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess() && maxVM().bytecodeBreakpointCount() > 0);
        }
    }


    /**
     * @param surrogate for a breakpoint in the VM.
     * @param title a string name for the Action, uses default name if null.
     * @return an Action that will enable the breakpoint
     */
    public final InspectorAction enableBreakpoint(TeleBreakpoint teleBreakpoint, String title) {
        return new EnableBreakpointAction(teleBreakpoint, title);
    }


    /**
     * Action: disables a specific  breakpoint in the VM.
     */
    final class DisableBreakpointAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Disable breakpoint";

        final TeleBreakpoint _teleBreakpoint;

        DisableBreakpointAction(TeleBreakpoint teleBreakpoint, String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _teleBreakpoint = teleBreakpoint;
        }

        @Override
        protected void procedure() {
            if (focus().breakpoint() == _teleBreakpoint) {
                focus().setBreakpoint(null);
            }
            _teleBreakpoint.setEnabled(false);
            inspection().refreshAll(false);
        }
    }

    /**
     * @param surrogate for a breakpoint in the VM.
     * @param title a string name for the Action, uses default name if null.
     * @return an Action that will disable the breakpoint
     */
    public final InspectorAction disableBreakpoint(TeleBreakpoint teleBreakpoint, String title) {
        return new DisableBreakpointAction(teleBreakpoint, title);
    }


    /**
     * Action:  toggle on/off a breakpoint at the target code location of the current focus.
     */
    final class ToggleTargetCodeBreakpointAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Toggle target code breakpoint";

        ToggleTargetCodeBreakpointAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _refreshableActions.append(this);
            focus().addListener(new InspectionFocusAdapter() {
                @Override
                public void codeLocationFocusSet(TeleCodeLocation codeLocation, boolean interactiveForNative) {
                    refresh(false);
                }
            });
        }

        @Override
        protected void procedure() {
            final Address targetCodeInstructionAddress = focus().codeLocation().targetCodeInstructionAddresss();
            if (!targetCodeInstructionAddress.isZero()) {
                TeleTargetBreakpoint breakpoint = maxVM().getTargetBreakpoint(targetCodeInstructionAddress);
                if (breakpoint == null) {
                    breakpoint = maxVM().makeTargetBreakpoint(targetCodeInstructionAddress);
                    focus().setBreakpoint(breakpoint);
                } else {
                    breakpoint.remove();
                    focus().setBreakpoint(null);
                }
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess()  && focus().codeLocation().hasTargetCodeLocation());
        }
    }

    private InspectorAction _toggleTargetCodeBreakpoint = new ToggleTargetCodeBreakpointAction(null);

    /**
     * @return an Action that will toggle on/off a breakpoint at the target code location of the current focus.
     */
    public final InspectorAction toggleTargetCodeBreakpoint() {
        return _toggleTargetCodeBreakpoint;
    }


    /**
     * Action:  sets a breakpoint at every label in the target method containing the current code location focus.
     */
    final class SetTargetCodeLabelBreakpointsAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Set breakpoint at every target code label";

        SetTargetCodeLabelBreakpointsAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _refreshableActions.append(this);
            focus().addListener(new InspectionFocusAdapter() {
                @Override
                public void codeLocationFocusSet(TeleCodeLocation codeLocation, boolean interactiveForNative) {
                    refresh(false);
                }
            });
        }

        @Override
        protected void procedure() {
            final Address address = focus().codeLocation().targetCodeInstructionAddresss();
            final TeleTargetRoutine teleTargetRoutine = maxVM().findTeleTargetRoutine(TeleTargetRoutine.class, address);
            if (teleTargetRoutine != null) {
                teleTargetRoutine.setTargetCodeLabelBreakpoints();
            } else {
                gui().errorMessage("Unable to find target method in which to set breakpoints");
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess() && focus().hasCodeLocation() && focus().codeLocation().hasTargetCodeLocation());
        }
    }

    private InspectorAction _setTargetCodeLabelBreakpoints = new SetTargetCodeLabelBreakpointsAction(null);

    /**
     * @return an Action that will set a breakpoint at every label in the target method containing the current code location focus.
     */
    public final InspectorAction setTargetCodeLabelBreakpoints() {
        return _setTargetCodeLabelBreakpoints;
    }


    /**
     * Action:  removes any breakpoints at labels in the target method containing the current code location focus.
     */
    final class RemoveTargetCodeLabelBreakpointsAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Remove breakpoint at every target code label";

        RemoveTargetCodeLabelBreakpointsAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _refreshableActions.append(this);
            focus().addListener(new InspectionFocusAdapter() {
                @Override
                public void codeLocationFocusSet(TeleCodeLocation codeLocation, boolean interactiveForNative) {
                    refresh(false);
                }
            });
            inspection().addInspectionListener(new InspectionListenerAdapter() {
                @Override
                public void breakpointSetChanged() {
                    refresh(true);
                }
            });
        }

        @Override
        protected void procedure() {
            final Address address = focus().codeLocation().targetCodeInstructionAddresss();
            final TeleTargetRoutine teleTargetRoutine = maxVM().findTeleTargetRoutine(TeleTargetRoutine.class, address);
            if (teleTargetRoutine != null) {
                teleTargetRoutine.removeTargetCodeLabelBreakpoints();
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess() && maxVM().targetBreakpointCount() > 0 && focus().hasCodeLocation() && focus().codeLocation().hasTargetCodeLocation());
        }
    }

    private InspectorAction _removeTargetCodeLabelBreakpoints = new RemoveTargetCodeLabelBreakpointsAction(null);

    /**
     * @return an Action that will remove any breakpoints labels in the target method containing the current code location focus.
     */
    public final InspectorAction removeTargetCodeLabelBreakpoints() {
        return _removeTargetCodeLabelBreakpoints;
    }


    /**
     * Action: removes all existing target code breakpoints in the VM.
     */
    final class RemoveAllTargetCodeBreakpointsAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Remove all target code breakpoints";

        RemoveAllTargetCodeBreakpointsAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _refreshableActions.append(this);
            inspection().addInspectionListener(new InspectionListenerAdapter() {
                @Override
                public void breakpointSetChanged() {
                    refresh(true);
                }
            });
        }

        @Override
        protected void procedure() {
            focus().setBreakpoint(null);
            for (TeleTargetBreakpoint targetBreakpoint : maxVM().targetBreakpoints()) {
                targetBreakpoint.remove();
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess() && maxVM().targetBreakpointCount() > 0);
        }
    }

    private InspectorAction _removeAllTargetCodeBreakpoints = new RemoveAllTargetCodeBreakpointsAction(null);

    /**
     * @return an Action that will remove all target code breakpoints in the VM.
     */
    public final InspectorAction removeAllTargetCodeBreakpoints() {
        return _removeAllTargetCodeBreakpoints;
    }


    /**
     * Action:  sets target code breakpoints at  method entries to be selected interactively by name.
     */
    final class SetTargetCodeBreakpointAtMethodEntriesByNameAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Compiled methods...";

        SetTargetCodeBreakpointAtMethodEntriesByNameAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            final TeleClassActor teleClassActor = ClassActorSearchDialog.show(inspection(), "Class for compiled method entry breakpoints...", "Select");
            if (teleClassActor != null) {
                final Sequence<TeleTargetMethod> teleTargetMethods = TargetMethodSearchDialog.show(inspection(), teleClassActor, "Compiled Method Entry Breakpoints", "Set Breakpoints", true);
                if (teleTargetMethods != null) {
                    // There may be multiple compilations of a method in the result.
                    TeleTargetBreakpoint teleTargetBreakpoint = null;
                    for (TeleTargetMethod teleTargetMethod : teleTargetMethods) {
                        teleTargetBreakpoint = teleTargetMethod.getTeleClassMethodActor().setTargetBreakpointAtEntry();
                    }
                    focus().setBreakpoint(teleTargetBreakpoint);
                }
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess());
        }
    }

    private final SetTargetCodeBreakpointAtMethodEntriesByNameAction _setTargetCodeBreakpointAtMethodEntriesByName =
        new SetTargetCodeBreakpointAtMethodEntriesByNameAction(null);

    /**
     * @return an interactive Action that sets a target code breakpoint at  a method entry to be selected by name.
     */
    public final InspectorAction setTargetCodeBreakpointAtMethodEntriesByName() {
        return _setTargetCodeBreakpointAtMethodEntriesByName;
    }


    /**
     * Action: sets target code breakpoint at object initializers of a class specified interactively by name.
     */
    final class SetTargetCodeBreakpointAtObjectInitializerAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Break in compiled object initializers of class...";

        SetTargetCodeBreakpointAtObjectInitializerAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            final TeleClassActor teleClassActor = ClassActorSearchDialog.show(inspection(), "Break in Object Initializers of Class...", "Set Breakpoint");
            if (teleClassActor != null) {
                final ClassActor classActor = teleClassActor.classActor();
                if (classActor.localVirtualMethodActors() != null) {
                    for (VirtualMethodActor virtualMethodActor : classActor.localVirtualMethodActors()) {
                        if (virtualMethodActor.name() == SymbolTable.INIT) {
                            final TeleClassMethodActor teleClassMethodActor = maxVM().findTeleMethodActor(TeleClassMethodActor.class, virtualMethodActor);
                            if (teleClassMethodActor != null) {
                                TeleTargetBreakpoint teleTargetBreakpoint = null;
                                for (TeleTargetMethod teleTargetMethod : teleClassMethodActor.targetMethods()) {
                                    teleTargetBreakpoint = teleTargetMethod.setTargetBreakpointAtEntry();
                                }
                                if (teleTargetBreakpoint != null) {
                                    focus().setBreakpoint(teleTargetBreakpoint);
                                }
                            }
                        }
                    }
                }
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess());
        }
    }

    private InspectorAction _setTargetCodeBreakpointAtObjectInitializer =
        new SetTargetCodeBreakpointAtObjectInitializerAction(null);

    /**
     * @return an interactive Action that will set a target code breakpoint at the
     * object initializer for a class specified by name.
     */
    public final InspectorAction setTargetCodeBreakpointAtObjectInitializer() {
        return _setTargetCodeBreakpointAtObjectInitializer;
    }


    /**
     * Action:  toggle on/off a breakpoint at the  bytecode location of the current focus.
     */
    class ToggleBytecodeBreakpointAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Toggle bytecode breakpoint";

        ToggleBytecodeBreakpointAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _refreshableActions.append(this);
            focus().addListener(new InspectionFocusAdapter() {
                @Override
                public void codeLocationFocusSet(TeleCodeLocation codeLocation, boolean interactiveForNative) {
                    refresh(false);
                }
            });
        }

        @Override
        protected void procedure() {
            final BytecodeLocation bytecodeLocation = focus().codeLocation().bytecodeLocation();
            if (bytecodeLocation != null) {
                final TeleBytecodeBreakpoint.Key key = new TeleBytecodeBreakpoint.Key(bytecodeLocation);
                final TeleBytecodeBreakpoint breakpoint = maxVM().getBytecodeBreakpoint(key);
                if (breakpoint == null) {
                    maxVM().makeBytecodeBreakpoint(key);
                } else {
                    breakpoint.remove();
                }
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess()  && focus().hasCodeLocation() && focus().codeLocation().hasBytecodeLocation());
        }
    }

    private InspectorAction _toggleBytecodeBreakpoint = new ToggleBytecodeBreakpointAction(null);

    /**
     * @return an Action that will toggle on/off a breakpoint at the bytecode location of the current focus.
     */
    public final InspectorAction toggleBytecodeBreakpoint() {
        return _toggleBytecodeBreakpoint;
    }


     /**
     * Action: sets a bytecode breakpoint at a method entry specified interactively by name.
     */
    final class SetBytecodeBreakpointAtMethodEntryByNameAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Method on classpath, by name...";

        SetBytecodeBreakpointAtMethodEntryByNameAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            final TypeDescriptor typeDescriptor = TypeSearchDialog.show(inspection(), "Class for bytecode method entry breakpoint...", "Select");
            if (typeDescriptor != null) {
                final MethodKey methodKey = MethodSearchDialog.show(inspection(), typeDescriptor, "Bytecode method entry breakpoint", "Set Breakpoint");
                if (methodKey != null) {
                    maxVM().makeBytecodeBreakpoint(new TeleBytecodeBreakpoint.Key(methodKey, 0));
                }
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess() && maxVM().activateMessenger());
        }
    }

    private final SetBytecodeBreakpointAtMethodEntryByNameAction _setBytecodeBreakpointAtMethodEntryByName =
        new SetBytecodeBreakpointAtMethodEntryByNameAction(null);

    /**
     * @return an interactive Action  that will set a target code breakpoint at  a method entry to be selected by name.
     */
    public final InspectorAction setBytecodeBreakpointAtMethodEntryByName() {
        return _setBytecodeBreakpointAtMethodEntryByName;
    }


    /**
     * Action: sets a bytecode breakpoint at a method entry specified interactively by name.
     */
    final class SetBytecodeBreakpointAtMethodEntryByKeyAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Method matched by key...";

        SetBytecodeBreakpointAtMethodEntryByKeyAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            final MethodKey methodKey = MethodKeyInputDialog.show(inspection(), "Specify method");
            if (methodKey != null) {
                maxVM().makeBytecodeBreakpoint(new TeleBytecodeBreakpoint.Key(methodKey, 0));
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess() && maxVM().activateMessenger());
        }
    }

    private final SetBytecodeBreakpointAtMethodEntryByKeyAction _setBytecodeBreakpointAtMethodEntryByKey =
        new SetBytecodeBreakpointAtMethodEntryByKeyAction(null);

    /**
     * @return an interactive Action  that will set a target code breakpoint at  a method entry to be selected by name.
     */
    public final InspectorAction setBytecodeBreakpointAtMethodEntryByKey() {
        return _setBytecodeBreakpointAtMethodEntryByKey;
    }


    /**
     * Action: removes all existing bytecode breakpoints in the VM.
     */
    final class RemoveAllBytecodeBreakpointsAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Remove all bytecode breakpoints";

        RemoveAllBytecodeBreakpointsAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _refreshableActions.append(this);
            inspection().addInspectionListener(new InspectionListenerAdapter() {
                @Override
                public void breakpointSetChanged() {
                    refresh(true);
                }
            });
        }

        @Override
        protected void procedure() {
            focus().setBreakpoint(null);
            for (TeleBytecodeBreakpoint bytecodeBreakpoint : maxVM().bytecodeBreakpoints()) {
                bytecodeBreakpoint.remove();
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess() && maxVM().bytecodeBreakpointCount() > 0);
        }
    }

    private InspectorAction _removeAllBytecodeBreakpoints = new RemoveAllBytecodeBreakpointsAction(null);

    /**
     * @return an Action that will remove all target code breakpoints in the VM.
     */
    public final InspectorAction removeAllBytecodeBreakpoints() {
        return _removeAllBytecodeBreakpoints;
    }


    final class SetWatchpointAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Set watchpoint...";

        SetWatchpointAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
        }

        @Override
        protected void procedure() {
            new AddressInputDialog(inspection(), maxVM().bootImageStart(), "Set word watch point at address...", "Watch") {
                @Override
                public void entered(Address address) {
                    final Address start = address;
                    final Size size = Size.fromInt(Word.size());
                    maxVM().makeWatchpoint(new RuntimeMemoryRegion(start, size));
                }
            };
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess()  && focus().codeLocation().hasTargetCodeLocation());
        }
    }

    private final SetWatchpointAction _setWatchpoint = new SetWatchpointAction(null);

    public final SetWatchpointAction setWatchpoint() {
        return _setWatchpoint;
    }

    /**
     * Action:  pause the running VM.
     */
    final class DebugPauseAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Pause process";

        DebugPauseAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            try {
                maxVM().pause();
            } catch (Exception exception) {
                gui().errorMessage("Pause could not be initiated", exception.toString());
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(focus().hasThread() && inspection().isVMRunning());
        }
    }

    private final DebugPauseAction _debugPause = new DebugPauseAction(null);

    public final DebugPauseAction debugPause() {
        return _debugPause;
    }


    /**
     * Action: resumes the running VM.
     */
    final class DebugResumeAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Resume";

        DebugResumeAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            try {
                maxVM().resume(false, false);
            } catch (Exception exception) {
                gui().errorMessage("Run to instruction could not be performed.", exception.toString());
            }
        }


        @Override
        public void refresh(boolean force) {
            setEnabled(focus().hasThread() && inspection().isVMReady());
        }
    }

    private final InspectorAction _debugResume = new DebugResumeAction(null);

     /**
     * @return an Action that will resume full execution of theVM.
     */
    public final InspectorAction debugResume() {
        return _debugResume;
    }


    /**
     * Action:  advance the currently selected thread until it returns from its current frame in the VM,
     * ignoring breakpoints.
     */
    final class DebugReturnFromFrameAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Return from frame (ignoring breakpoints)";

        DebugReturnFromFrameAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            final Address returnAddress = focus().thread().getReturnAddress();
            if (returnAddress != null) {
                try {
                    maxVM().runToInstruction(returnAddress, false, true);
                } catch (Exception exception) {
                    gui().errorMessage("Return from frame (ignoring breakpoints) could not be performed.", exception.toString());
                }
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(focus().hasThread() && inspection().isVMReady());
        }
    }

    private final InspectorAction _debugReturnFromFrame = new DebugReturnFromFrameAction(null);

    /**
     * @return an Action that will resume execution in the VM, stopping at the first instruction after returning
     *         from the current frame of the currently selected thread
     */
    public final InspectorAction debugReturnFromFrame() {
        return _debugReturnFromFrame;
    }


    /**
     * Action:  advance the currently selected thread until it returns from its current frame
     * or hits a breakpoint in the VM.
     */
    final class DebugReturnFromFrameWithBreakpointsAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Return from frame";

        DebugReturnFromFrameWithBreakpointsAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _refreshableActions.append(this);
        }

        @Override
        public void procedure() {
            final Address returnAddress = focus().thread().getReturnAddress();
            if (returnAddress != null) {
                try {
                    maxVM().runToInstruction(returnAddress, false, false);
                } catch (Exception exception) {
                    gui().errorMessage("Return from frame could not be performed.", exception.toString());
                }
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(focus().hasThread() && inspection().isVMReady());
        }
    }

    private final InspectorAction _debugReturnFromFrameWithBreakpoints = new DebugReturnFromFrameWithBreakpointsAction(null);

    /**
     * @return an Action that will resume execution in the VM, stopping at the first instruction after returning
     *         from the current frame of the currently selected thread, or at a breakpoint, whichever comes first.
     */
    public final InspectorAction debugReturnFromFrameWithBreakpoints() {
        return _debugReturnFromFrameWithBreakpoints;
    }


    /**
     * Action:  advance the currently selected thread in the VM until it reaches the selected instruction,
     * ignoring breakpoints.
     */
    final class DebugRunToSelectedInstructionAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Run to selected instruction (ignoring breakpoints)";

        DebugRunToSelectedInstructionAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            final Address selectedAddress = focus().codeLocation().targetCodeInstructionAddresss();
            if (!selectedAddress.isZero()) {
                try {
                    maxVM().runToInstruction(selectedAddress, false, true);
                } catch (Exception exception) {
                    throw new InspectorError("Run to instruction (ignoring breakpoints) could not be performed.", exception);
                }
            } else {
                gui().errorMessage("No instruction selected");
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(focus().hasThread() && focus().hasCodeLocation() && inspection().isVMReady());
        }
    }

    private final InspectorAction _debugRunToSelectedInstruction = new DebugRunToSelectedInstructionAction(null);

    /**
     * @return an Action that will resume execution in the VM, stopping at the the currently
     * selected instruction, ignoring breakpoints.
     */
    public final InspectorAction debugRunToSelectedInstruction() {
        return _debugRunToSelectedInstruction;
    }


    /**
     * Action:  advance the currently selected thread in the VM until it reaches the selected instruction
     * or a breakpoint, whichever comes first.
     */
    final class DebugRunToSelectedInstructionWithBreakpointsAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Run to selected instruction";

        DebugRunToSelectedInstructionWithBreakpointsAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            final Address selectedAddress = focus().codeLocation().targetCodeInstructionAddresss();
            if (!selectedAddress.isZero()) {
                try {
                    maxVM().runToInstruction(selectedAddress, false, false);
                } catch (Exception exception) {
                    throw new InspectorError("Run to selection instruction could not be performed.", exception);
                }
            } else {
                gui().errorMessage("No instruction selected");
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(focus().hasThread() && focus().hasCodeLocation() && inspection().isVMReady());
        }
    }

    private final InspectorAction _debugRunToSelectedInstructionWithBreakpoints = new DebugRunToSelectedInstructionWithBreakpointsAction(null);

    /**
     * @return an Action that will resume execution in the VM, stopping at the selected instruction
     * or a breakpoint, whichever comes first..
     */
    public final InspectorAction debugRunToSelectedInstructionWithBreakpoints() {
        return _debugRunToSelectedInstructionWithBreakpoints;
    }


    /**
     * Action:  advance the currently selected thread in the VM until it reaches the next call instruction,
     * ignoring breakpoints; fails if there is no known call in the method containing the IP.
     */
    final class DebugRunToNextCallAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Run to next call instruction (ignoring breakpoints)";

        DebugRunToNextCallAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            final Address address = focus().codeLocation().targetCodeInstructionAddresss();
            final TeleTargetMethod teleTargetMethod = maxVM().findTeleTargetRoutine(TeleTargetMethod.class, address);
            if (teleTargetMethod != null) {
                final Address nextCallAddress = teleTargetMethod.getNextCallAddress(address);
                if (!nextCallAddress.isZero()) {
                    try {
                        maxVM().runToInstruction(nextCallAddress, false, true);
                    } catch (Exception exception) {
                        throw new InspectorError("Run to next call instruction (ignoring breakpoints) could not be performed.", exception);
                    }
                }
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(focus().hasThread() && focus().hasCodeLocation() && inspection().isVMReady());
        }
    }

    private final InspectorAction _debugRunToNextCall = new DebugRunToNextCallAction(null);

    /**
     * @return an Action that will resume execution in the VM, stopping at the next call instruction,
     * ignoring breakpoints; fails if there is no known call in the method containing the IP.
     */
    public final InspectorAction debugRunToNextCall() {
        return _debugRunToNextCall;
    }


    /**
     * Action:  advance the currently selected thread in the VM until it reaches the selected instruction
     * or a breakpoint.
     */
    final class DebugRunToNextCallWithBreakpointsAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Run to next call instruction";

        DebugRunToNextCallWithBreakpointsAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            final Address address = focus().codeLocation().targetCodeInstructionAddresss();
            final TeleTargetMethod teleTargetMethod = maxVM().findTeleTargetRoutine(TeleTargetMethod.class, address);
            if (teleTargetMethod != null) {
                final Address nextCallAddress = teleTargetMethod.getNextCallAddress(address);
                if (!nextCallAddress.isZero()) {
                    try {
                        maxVM().runToInstruction(nextCallAddress, false, false);
                    } catch (Exception exception) {
                        throw new InspectorError("Run to next call instruction could not be performed.", exception);
                    }
                }
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(focus().hasThread() && focus().hasCodeLocation() && inspection().isVMReady());
        }
    }

    private final InspectorAction _debugNextRunToCallWithBreakpoints = new DebugRunToNextCallWithBreakpointsAction(null);

    /**
     * @return an Action that will resume execution in the VM, stopping at the first instruction after returning
     *         from the current frame of the currently selected thread, or at a breakpoint, whichever comes first.
     */
    public final InspectorAction debugRunToNextCallWithBreakpoints() {
        return _debugNextRunToCallWithBreakpoints;
    }


    /**
     * Action:  advances the currently selected thread one step in the VM.
     */
    class DebugSingleStepAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Single instruction step";

        DebugSingleStepAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _refreshableActions.append(this);
        }

        @Override
        public  void procedure() {
            final TeleNativeThread thread = focus().thread();
            try {
                maxVM().singleStep(thread, false);
            } catch (Exception exception) {
                gui().errorMessage("Couldn't single step", exception.toString());
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(focus().hasThread() && inspection().isVMReady());
        }
    }

    private final InspectorAction _debugSingleStep = new DebugSingleStepAction(null);

    /**
     * @return an action that will single step the currently selected thread in the VM
     */
    public final InspectorAction debugSingleStep() {
        return _debugSingleStep;
    }


    /**
     * Action:   resumes execution of the VM, stopping at the one immediately after the current
     *         instruction of the currently selected thread.
     */
    final class DebugStepOverAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Step over (ignoring breakpoints)";

        DebugStepOverAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            final TeleNativeThread thread = focus().thread();
            try {
                maxVM().stepOver(thread, false, true);
            } catch (Exception exception) {
                gui().errorMessage("Step over (ignoring breakpoints) could not be performed.", exception.toString());
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(focus().hasThread() && inspection().isVMReady());
        }
    }

    private final InspectorAction _debugStepOver = new DebugStepOverAction(null);

    /**
     * @return an Action that will resume execution of the VM, stopping at the one immediately after the current
     *         instruction of the currently selected thread
     */
    public final InspectorAction debugStepOver() {
        return _debugStepOver;
    }


    /**
     * Action:   resumes execution of the VM, stopping at the one immediately after the current
     *         instruction of the currently selected thread or at a breakpoint.
     */
    final class DebugStepOverWithBreakpointsAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Step over";

        DebugStepOverWithBreakpointsAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            final TeleNativeThread thread = focus().thread();
            try {
                maxVM().stepOver(thread, false, false);
            } catch (Exception exception) {
                gui().errorMessage("Step over could not be performed.", exception.toString());
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(focus().hasThread() && inspection().isVMReady());
        }
    }

    private final InspectorAction _debugStepOverWithBreakpoints = new DebugStepOverWithBreakpointsAction(null);

    /**
     * @return an Action that will resume execution of the VM, stopping at the one immediately after the current
     *         instruction of the currently selected thread
     */
    public final InspectorAction debugStepOverWithBreakpoints() {
        return _debugStepOverWithBreakpoints;
    }


    /**
     * Action:  displays and highlights an inspection of the current Java frame descriptor.
     */
    final class InspectJavaFrameDescriptorAction extends InspectorAction {
        private static final String DEFAULT_TITLE = "Inspect Java frame descriptor";
        private TargetJavaFrameDescriptor _targetJavaFrameDescriptor;
        private TargetABI _abi;

        InspectJavaFrameDescriptorAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
            _refreshableActions.append(this);
            focus().addListener(new InspectionFocusAdapter() {
                @Override
                public void codeLocationFocusSet(TeleCodeLocation codeLocation, boolean interactiveForNative) {
                    refresh(false);
                }
            });
        }

        @Override
        protected void procedure() {
            assert _targetJavaFrameDescriptor != null;
            TargetJavaFrameDescriptorInspector.make(inspection(), _targetJavaFrameDescriptor, _abi).highlight();
        }

        /**
         * @return whether there is a Java frame descriptor at the focus target code location
         */
        private boolean inspectable() {
            final Address instructionAddress = focus().codeLocation().targetCodeInstructionAddresss();
            if (instructionAddress.isZero()) {
                return false;
            }
            final TeleTargetMethod teleTargetMethod = maxVM().makeTeleTargetMethod(instructionAddress);
            if (teleTargetMethod != null) {
                final int stopIndex = teleTargetMethod.getJavaStopIndex(instructionAddress);
                if (stopIndex >= 0) {
                    _targetJavaFrameDescriptor = teleTargetMethod.getJavaFrameDescriptor(stopIndex);
                    if (_targetJavaFrameDescriptor == null) {
                        return false;
                    }
                    _abi = teleTargetMethod.getAbi();
                    return true;
                }
            }
            _targetJavaFrameDescriptor = null;
            _abi = null;
            return false;
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspectable());
        }
    }

    private InspectorAction _inspectJavaFrameDescriptor = new InspectJavaFrameDescriptorAction(null);

    /**
     * @return an Action that will display an inspection of the current Java frame descriptor.
     */
    public final InspectorAction inspectJavaFrameDescriptor() {
        return _inspectJavaFrameDescriptor;
    }





    /**
     * Action:  makes visible and highlight the {@link FocusInspector}.
     */
    final class ViewFocusAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "View User Focus";

        ViewFocusAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
        }

        @Override
        protected void procedure() {
            FocusInspector.make(inspection()).highlight();
        }
    }

    private InspectorAction _viewFocus = new ViewFocusAction(null);

    /**
     * @return an Action that will make visible the {@link FocusInspector}.
     */
    public final InspectorAction viewFocus() {
        return _viewFocus;
    }


    /**
     * Action:  lists to the console this history of the VM state.
     */
    final class ListVMStateHistoryAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "List VM state history";

        ListVMStateHistoryAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
        }

        @Override
        protected void procedure() {
            maxVM().describeVMStateHistory(System.out);
        }
    }

    private InspectorAction _listVMStateHistory = new ListVMStateHistoryAction(null);

    /**
     * @return an Action that will list to the console the history of the VM state
     */
    public final InspectorAction listVMStateHistory() {
        return _listVMStateHistory;
    }


    /**
     * Action:  lists to the console all entries in the {@link TeleCodeRegistry}.
     */
    final class ListCodeRegistryAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "List code registry contents";

        ListCodeRegistryAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
        }

        @Override
        protected void procedure() {
            maxVM().describeTeleTargetRoutines(System.out);
        }
    }

    private InspectorAction _listCodeRegistry = new ListCodeRegistryAction(null);

    /**
     * @return an Action that will list to the console the entries in the {@link TeleCodeRegistry}.
     */
    public final InspectorAction listCodeRegistry() {
        return _listCodeRegistry;
    }


    /**
     * Action:  lists to the console all entries in the {@link TeleCodeRegistry} to an interactively specified file.
     */
    final class ListCodeRegistryToFileAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "List code registry contents to a file...";

        ListCodeRegistryToFileAction(String title) {
            super(inspection(), title == null ? DEFAULT_TITLE : title);
        }

        @Override
        protected void procedure() {
            //Create a file chooser
            final JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogType(JFileChooser.SAVE_DIALOG);
            fileChooser.setDialogTitle("Save code registry summary to file:");
            final int returnVal = fileChooser.showSaveDialog(inspection().gui().frame());
            if (returnVal != JFileChooser.APPROVE_OPTION) {
                return;
            }
            final File file = fileChooser.getSelectedFile();
            if (file.exists() && !gui().yesNoDialog("File " + file + "exists.  Overwrite?\n")) {
                return;
            }
            try {
                final PrintStream printStream = new PrintStream(new FileOutputStream(file, false));
                maxVM().describeTeleTargetRoutines(printStream);
            } catch (FileNotFoundException fileNotFoundException) {
                gui().errorMessage("Unable to open " + file + " for writing:" + fileNotFoundException);
            }
        }
    }

    private InspectorAction _listCodeRegistryToFile = new ListCodeRegistryToFileAction(null);

    /**
     * @return an interactive Action that will list to a specified file the entries in the {@link TeleCodeRegistry}.
     */
    public final InspectorAction listCodeRegistryToFile() {
        return _listCodeRegistryToFile;
    }

}
