package steam.boiler.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import steam.boiler.model.SteamBoilerController;
import steam.boiler.util.Mailbox;
import steam.boiler.util.Mailbox.Message;
import steam.boiler.util.Mailbox.MessageKind;
import steam.boiler.util.Mailbox.Mode;
import steam.boiler.util.SteamBoilerCharacteristics;

/**
 * The Steam Boiler controller for the simulation.
 * 
 * @author Vaishnav Ajith
 *
 */

public class MySteamBoilerController implements SteamBoilerController {

  /**
   * Captures the various modes in which the controller can operate.
   *
   * @author David J. Pearce
   *
   */
  private enum State {

    /**
     * The controller is in a waiting state, it checks if the senors and the water
     * level are appropriate in this state.
     */
    WAITING,
    /**
     * The controller is in a ready state, it just makes sures that the physical
     * units are ready before it enter normal state.
     */
    READY,
    /**
     * The controller is in a normal state, it makes sures that the water level in
     * the steam boiler is appropriate.
     */
    NORMAL,
    /**
     * The controller is in a degraded state, it runs like it is in a normal state
     * while handling the failures from the physical units.
     */
    DEGRADED,
    /**
     * The controller is in a resuce state, it runs like it is in a normal state
     * while handling the failures from the water level sensor.
     */
    RESCUE,
    /**
     * The controller is in an Emergency stop state, which causes the steam boiler
     * to completely stop immediately.
     */
    EMERGENCY_STOP
  }

  /**
   * Describes the type of failure that the SteamBoiler might be experiencing.
   * 
   * @author David J. Pearce
   *
   */
  private enum Failure {

    /**
     * Controller has detected a pump failure.
     */
    PUMP_STATE_FAIL,
    /**
     * Controller has detected a pump controller failure.
     */
    PUMP_CONTROL_STATE_FAIL,
    /**
     * Controller has detected a water level failure.
     */
    WATER_LEVEL_FAIL,
    /**
     * Controller has detected a steam level failure.
     */
    STEAM_LEVEL_FAIL,
    /**
     * Controller has detected no failures.
     */
    NONE,
  }

  /**
   * Maximum quantity of water in the steam-boiler .
   */
  private final double maxLimit;
  /**
   * Minimum quantity of water in the steam-boiler .
   */
  private final double minLimit;
  /**
   * Maximum quantity of water in the steam-boiler during normal circumstance.
   */
  private final double maxNormal;
  /**
   * Minimum quantity of water in the steam-boiler during normal circumstance.
   */
  private final double minNormal;
  /**
   * The ideal quantity of water in the steam-boiler during normal circumstance.
   */
  private final double normalMidPointWaterLevel;
  /**
   * The number of pumps that the steam-boiler has.
   */
  private final double numberOfPumps;
  /**
   * The current steam level in the steam-boiler in the current cycle.
   */
  private double currentSteamLevel;
  /**
   * The previous steam level in the steam-boiler in the previous cycle.
   */
  private double previousSteamLevel;
  /**
   * The previous ideal quantity of water in the steam-boiler during normal
   * circumstance predicted in the last cycle.
   */
  private double previousidealPredictedWater = 0;
  /**
   * The ideal quantity of water in the steam-boiler during normal circumstance
   * predicted in the current cycle.
   */
  private double idealPredictedWater;
  /**
   * Indicates how many pumps have been turned on.
   */
  private double numberOfActivePumps;
  /**
   * Indicates each pumps pump capacity.
   */
  private double[] pumpCapacity;
  /**
   * The maximum steam rate for the steam-boiler.
   */
  private double maximalSteamRate;
  /**
   * An array of the predicted highest possible quantity of water in the steam
   * boiler during normal circumstance predicted in the current cycle for each
   * pump.
   */
  private double[] predictedMaxWaterLevels;
  /**
   * An array of the predicted lowest possible quantity of water in the steam
   * boiler during normal circumstance predicted in the current cycle.
   */
  private double[] predictedMinWaterLevels;

  /**
   * Records the configuration characteristics for the given boiler problem.
   */
  private final SteamBoilerCharacteristics configuration;

  /**
   * Identifies the current mode in which the controller is operating.
   */
  private State mode = State.WAITING;

  /**
   * Identifies the current mode in which the controller is operating.
   */
  private Failure failureMode = Failure.NONE;

  /**
   * An array of the predicted average water level during normal circumstance
   * predicted in the current cycle.
   */

  private double[] predictedMidPointWaterLevels;

  /**
   * Identifies the midpoint waterLevel.
   */
  private boolean initilization;

  /**
   * Identifies the previous waterLevel.
   */
  private double previousWaterLevel;

  /**
   * Identifies the current waterLevel.
   */
  private double waterLevel;
  /**
   * Identifies whether the valve is open or not.
   */
  private boolean valveOpen;
  /**
   * Identifies which pumps are open or closed.
   */
  private boolean[] openOrClosedPumps;
  /**
   * Indicates which pump failed.
   */
  private int failPump;

  // ----------------------------------------------------------------------------
  /**
   * Construct a steam boiler controller for a given set of characteristics.
   *
   * @param configuration The boiler characteristics to be used.
   */

  public MySteamBoilerController(SteamBoilerCharacteristics configuration) {
    assert configuration != null;

    this.configuration = configuration;
    this.mode = State.WAITING;
    this.minNormal = configuration.getMinimalNormalLevel();
    this.maxNormal = configuration.getMaximalNormalLevel();
    this.maxLimit = configuration.getMaximalLimitLevel();
    this.minLimit = configuration.getMinimalLimitLevel();
    this.waterLevel = 0;
    this.valveOpen = false;
    this.maximalSteamRate = configuration.getMaximualSteamRate();
    this.numberOfPumps = configuration.getNumberOfPumps();
    this.numberOfActivePumps = 0;
    this.pumpCapacity = new double[(int) this.numberOfPumps];
    this.openOrClosedPumps = new boolean[(int) this.numberOfPumps];
    this.predictedMaxWaterLevels = new double[(int) this.numberOfPumps + 1];
    this.predictedMinWaterLevels = new double[(int) this.numberOfPumps + 1];
    this.predictedMidPointWaterLevels = new double[(int) this.numberOfPumps + 1];
    this.normalMidPointWaterLevel = (this.maxNormal + this.minNormal) / 2;
    this.idealPredictedWater = 0;
    this.previousSteamLevel = 0;
    this.previousWaterLevel = 0;
    this.initilization = false;
    this.failureMode = Failure.NONE;

  }

  /**
   * This message is displayed in the simulation window, and enables a limited
   * form of debug output. The content of the message has no material effect on
   * the system, and can be whatever is desired. In principle, however, it should
   * display a useful message indicating the current state of the controller.
   *
   * @return The current state of the controller
   */
  @Override
  @Nullable
  public String getStatusMessage() {
    return this.mode.toString();
  }

  /**
   * Process a clock signal which occurs every 5 seconds. This requires reading
   * the set of incoming messages from the physical units and producing a set of
   * output messages which are sent back to them.
   *
   * @param incoming The set of incoming messages from the physical units.
   * @param outgoing Messages generated during the execution of this method should
   *                 be written here.
   */
  @Override
  public void clock(@NonNull Mailbox incoming, @NonNull Mailbox outgoing) {
    assert outgoing != null;
    assert incoming != null;

    // Extract expected messages
    Message levelMessage = extractOnlyMatch(MessageKind.LEVEL_v, incoming);
    Message steamMessage = extractOnlyMatch(MessageKind.STEAM_v, incoming);
    Message[] pumpStateMessages = extractAllMatches(MessageKind.PUMP_STATE_n_b, incoming);
    Message[] pumpControlStateMessages = extractAllMatches(MessageKind.PUMP_CONTROL_STATE_n_b,
        incoming);

    // Checking for transmission failure
    if (transmissionFailure(levelMessage, steamMessage, pumpStateMessages,
        pumpControlStateMessages)) {
      // Level and steam messages required, so emergency stop.
      this.mode = State.EMERGENCY_STOP;

    }

    // Enters Emergency mode if there was transmission failure,
    // otherwise the program will continue normally
    if (this.mode == State.EMERGENCY_STOP) {
      outgoing.send(new Message(Mailbox.MessageKind.MODE_m, Mode.EMERGENCY_STOP));
    } else {

      assert levelMessage != null;
      assert steamMessage != null;

      // Checks if physical units have any failures
      unitChecks(outgoing, pumpStateMessages, pumpControlStateMessages);
      negativeSensorsCheck(levelMessage, steamMessage);

      // Describes the operations the controller would be undergoing based on it
      // current state.
      if (this.mode == State.WAITING) {

        // While waiting, check if sensor and water level are adequate
        // If adequate, the controller will enter ready state
        this.initilization = false;
        Message steamBoilerWaitingMessage = extractOnlyMatch(MessageKind.STEAM_BOILER_WAITING,
            incoming);
        if (steamBoilerWaitingMessage != null) {
          if (initializingSensorCheck(levelMessage, steamMessage)) {
            initializingWaterLevels(levelMessage, outgoing);
          }
        }
      }

      if (this.mode == State.READY) {
        this.initilization = false;
        steamLevelAssigning(steamMessage);

        // check for steam failure
        if (this.currentSteamLevel < this.previousSteamLevel
            || this.currentSteamLevel > this.maximalSteamRate) {
          this.mode = State.DEGRADED;
          this.failureMode = Failure.STEAM_LEVEL_FAIL;
          outgoing.send(new Message(Mailbox.MessageKind.MODE_m, Mode.DEGRADED));
          outgoing.send(new Message(MessageKind.STEAM_FAILURE_DETECTION));

        }

        // check if physical units are ready
        Message physicalUnitsMessage = extractOnlyMatch(MessageKind.PHYSICAL_UNITS_READY, incoming);
        if (physicalUnitsMessage != null) {
          this.mode = State.NORMAL;
          outgoing.send(new Message(Mailbox.MessageKind.MODE_m, Mode.NORMAL));
          this.initilization = true;

        }
      }

      // if it's degraded or rescue mode, operate like its in normal mode
      // while handling the physical unit failures
      if (this.mode == State.DEGRADED) {
        steamLevelAssigning(steamMessage);
        waterLevelAssigning(levelMessage);

        if (unitChecks(outgoing, pumpStateMessages, pumpControlStateMessages)) {
          turnPumpsOn(idealPumpsOn(steamMessage), outgoing, pumpControlStateMessages);
        }
        handleFailureMode(incoming, outgoing);

      }
      if (this.mode == State.RESCUE) {
        steamLevelAssigning(steamMessage);
        waterLevelAssigning(levelMessage);

        if (unitChecks(outgoing, pumpStateMessages, pumpControlStateMessages)) {
          turnPumpsOn(idealPumpsOn(steamMessage), outgoing, pumpControlStateMessages);
        }
        handleFailureMode(incoming, outgoing);

      }

      if (this.mode == State.NORMAL) {
        steamLevelAssigning(steamMessage);
        waterLevelAssigning(levelMessage);

        if (unitChecks(outgoing, pumpStateMessages, pumpControlStateMessages)) {
          turnPumpsOn(idealPumpsOn(steamMessage), outgoing, pumpControlStateMessages);
        }

      }
      if (this.mode == State.EMERGENCY_STOP) {
        outgoing.send(new Message(Mailbox.MessageKind.MODE_m, Mode.EMERGENCY_STOP));
      } else {
        outgoing.send(new Message(MessageKind.MODE_m, Mailbox.Mode.INITIALISATION));
      }
    }
  }

  /**
   * It handles the failed state of the steam-boiler based on what failure mode it
   * is on currently.
   * 
   * @param incoming : Messages recieved from physical units
   * @param outgoing : Messages sent to physical units
   */
  private void handleFailureMode(Mailbox incoming, Mailbox outgoing) {
    assert incoming != null;
    assert outgoing != null;

    // Failure with pump controller
    if (this.failureMode == Failure.PUMP_CONTROL_STATE_FAIL) {
      Message failureAcknowledgement = extractOnlyMatch(
          MessageKind.PUMP_CONTROL_FAILURE_ACKNOWLEDGEMENT_n, incoming);
      Message repairAcknowledgement = extractOnlyMatch(MessageKind.PUMP_REPAIRED_n, incoming);

      if (failureAcknowledgement != null) {
        // do nothing
      }
      if (repairAcknowledgement != null) {
        this.mode = State.NORMAL;
        outgoing.send(new Message(Mailbox.MessageKind.MODE_m, Mode.NORMAL));
      }

    }

    // Failure with pump
    if (this.failureMode == Failure.PUMP_STATE_FAIL) {
      Message failureAcknowledgement = extractOnlyMatch(MessageKind.PUMP_REPAIRED_ACKNOWLEDGEMENT_n,
          incoming);
      Message repairAcknowledgement = extractOnlyMatch(MessageKind.PUMP_REPAIRED_n, incoming);

      if (failureAcknowledgement != null) {
        // do nothing
      }
      if (repairAcknowledgement != null) {
        this.mode = State.NORMAL;
        outgoing.send(new Message(Mailbox.MessageKind.MODE_m, Mode.NORMAL));
      }
    }

    // Failure with steam sensors
    if (this.failureMode == Failure.STEAM_LEVEL_FAIL) {
      Message failureAcknowledgement = extractOnlyMatch(
          MessageKind.STEAM_OUTCOME_FAILURE_ACKNOWLEDGEMENT, incoming);
      Message repairAcknowledgement = extractOnlyMatch(MessageKind.STEAM_REPAIRED, incoming);
      if (failureAcknowledgement != null) {
        // do nothing
      }
      if (repairAcknowledgement != null) {
        this.mode = State.NORMAL;
        outgoing.send(new Message(Mailbox.MessageKind.MODE_m, Mode.NORMAL));
      }

    }

    // failure with water sensors
    if (this.failureMode == Failure.WATER_LEVEL_FAIL) {
      Message failureAcknowledgement = extractOnlyMatch(MessageKind.LEVEL_FAILURE_ACKNOWLEDGEMENT,
          incoming);
      Message repairAcknowledgement = extractOnlyMatch(MessageKind.LEVEL_REPAIRED, incoming);
      if (failureAcknowledgement != null) {
        // do nothing
      }
      if (repairAcknowledgement != null) {
        this.mode = State.NORMAL;
        outgoing.send(new Message(Mailbox.MessageKind.MODE_m, Mode.NORMAL));
      }
    }
  }

  /**
   * Makes sure that the water level is within the normal range, when the
   * controller knows it is within the acceptable range it lets the physical units
   * know.
   * 
   * @param levelMessage : indicates water level status
   * @param outgoing     : Outgoing messages sent to physical units
   */
  private void initializingWaterLevels(Message levelMessage, Mailbox outgoing) {
    assert levelMessage != null;
    assert outgoing != null;

    waterLevelAssigning(levelMessage);

    // if the Q of water is above max Normal, activate valve
    if (levelMessage.getDoubleParameter() > this.maxNormal && !this.valveOpen) {
      this.valveOpen = true;
      outgoing.send(new Message(MessageKind.VALVE));
    }
    // if the Q of water is below min Normal, activate pump
    if (this.waterLevel < this.minNormal) {
      for (int i = 0; i < this.numberOfPumps; i++) {
        outgoing.send(new Message(MessageKind.OPEN_PUMP_n, i));
        this.openOrClosedPumps[i] = true;
        if (this.numberOfActivePumps < this.numberOfPumps) {
          this.numberOfActivePumps++;
        }
      }
    }

    // if failure of Water level detection, enter ES mode
    // if Q of water between the normal range, enter Ready mode
    if (this.waterLevel >= this.minNormal && this.waterLevel <= this.maxNormal) {
      this.mode = State.READY;
      outgoing.send(new Message(MessageKind.PROGRAM_READY));
    }
  }

  /**
   * Used to turn on or of pumps if the ideal number of pumps aren't turned on.
   * 
   * @param numberOfPumpsNeeded      : The ideal amount of pumps needed in the
   *                                 current pump cycle.
   * @param outgoing                 : Messages sent to the phsyical units
   * @param pumpControlStateMessages : Messages about the pump controllers
   */
  private void turnPumpsOn(int numberOfPumpsNeeded, Mailbox outgoing,
      Message[] pumpControlStateMessages) {
    assert outgoing != null;
    assert pumpControlStateMessages != null;

    // Asking the physical units to turn on more pumps if needed
    if (numberOfPumpsNeeded > this.numberOfActivePumps) {
      for (int i = 0; i < this.numberOfPumps; i++) {
        if (numberOfPumpsNeeded > this.numberOfActivePumps
            && !pumpControlStateMessages[i].getBooleanParameter()) {
          outgoing.send(new Message(MessageKind.OPEN_PUMP_n, i));
          this.numberOfActivePumps++;
          this.openOrClosedPumps[i] = true;
        }
      }
    }

    // Asking the physical units to turn of more pumps if needed
    if (numberOfPumpsNeeded < this.numberOfActivePumps) {
      for (int i = 0; i < this.numberOfPumps; i++) {
        if (numberOfPumpsNeeded < this.numberOfActivePumps
            && pumpControlStateMessages[i].getBooleanParameter()) {
          outgoing.send(new Message(MessageKind.CLOSE_PUMP_n, i));
          this.numberOfActivePumps--;
          this.openOrClosedPumps[i] = false;

        }
      }
    }
  }

  /**
   * Finds the ideal numeber of pumps needed in the current pump cycle.
   * 
   * @param steamMessage : Messages about the steam level
   * @return The number of pumps required
   */

  private int idealPumpsOn(Message steamMessage) {
    assert steamMessage != null;

    this.numberOfActivePumps = checkingActivePumps();
    double absoluteDifferenceWaterLevel = 200;
    int idealNumberOfPumps = 0;

    for (int i = 0; i <= this.numberOfPumps; i++) {

      // used to determine the predicted water levels when the number of pumps is 0
      if (i == 0) {
        double predictedMaxWaterLevel = this.waterLevel - (5 * steamMessage.getDoubleParameter());
        double predictedMinWaterLevel = this.waterLevel - (5 * this.maximalSteamRate);

        this.predictedMaxWaterLevels[i] = predictedMaxWaterLevel;
        this.predictedMinWaterLevels[i] = predictedMinWaterLevel;
        this.predictedMidPointWaterLevels[i] = (predictedMinWaterLevel + predictedMaxWaterLevel)
            / 2;

        // checks if 0 is the ideal number of pumnps
        if (Math.abs(this.predictedMidPointWaterLevels[i]
            - this.normalMidPointWaterLevel) < absoluteDifferenceWaterLevel) {
          absoluteDifferenceWaterLevel = Math
              .abs(this.predictedMidPointWaterLevels[i] - this.normalMidPointWaterLevel);
          idealNumberOfPumps = i;
        }

        // used to determine the predicted water levels when the number of pumps is
        // above 0
      } else {
        this.pumpCapacity[i - 1] = this.configuration.getPumpCapacity(i - 1);
        double predictedMaxWaterLevel = this.waterLevel + (5 * this.pumpCapacity[i - 1] * (i))
            - (5 * steamMessage.getDoubleParameter());
        double predictedMinWaterLevel = this.waterLevel + (5 * this.pumpCapacity[i - 1] * (i))
            - (5 * this.maximalSteamRate);

        this.predictedMaxWaterLevels[i] = predictedMaxWaterLevel;
        this.predictedMinWaterLevels[i] = predictedMinWaterLevel;
        this.predictedMidPointWaterLevels[i] = (predictedMinWaterLevel + predictedMaxWaterLevel)
            / 2;

        // checks if i is the ideal number of pumnps
        if (Math.abs(this.predictedMidPointWaterLevels[i]
            - this.normalMidPointWaterLevel) < absoluteDifferenceWaterLevel) {
          absoluteDifferenceWaterLevel = Math
              .abs(this.predictedMidPointWaterLevels[i] - this.normalMidPointWaterLevel);
          idealNumberOfPumps = i;
        }
      }
    }

    this.setPreviousidealPredictedWater(this.idealPredictedWater);
    this.idealPredictedWater = this.predictedMidPointWaterLevels[idealNumberOfPumps];

    assertTrue(checkingActivePumps() == this.numberOfActivePumps, "Checking active pumps"); //$NON-NLS-1$
    return idealNumberOfPumps;
  }

  /**
   * Stores the failed pump value.
   * 
   * @param pumps : The failed pump value
   */
  public void storePumpFailure(int pumps) {
    this.setFailPump(pumps);
  }

  /**
   * Used to find whether there is any kind of failures that the physical units
   * might be experiencing. If so then the controller will either go into
   * emergency stop or degraded mode depending on the failure.
   * 
   * @param outgoing                 : Messages sent to the physical units
   * @param pumpStateMessages        : Meassages about the state of each pump
   * @param pumpControlStateMessages : Meassages about the state of each pump
   *                                 controller
   * @return Whether the units all pass the checks or not
   */
  private boolean unitChecks(Mailbox outgoing, Message[] pumpStateMessages,
      Message[] pumpControlStateMessages) {
    assert outgoing != null;
    assert pumpStateMessages != null;
    assert pumpControlStateMessages != null;

    boolean returnCheck = true;

    // check if pumps has any failures
    if (checkingPumps(pumpStateMessages) != -1) {
      this.mode = State.DEGRADED;
      this.failureMode = Failure.PUMP_STATE_FAIL;

      outgoing.send(new Message(Mailbox.MessageKind.MODE_m, Mode.DEGRADED));
      outgoing.send(
          new Message(MessageKind.PUMP_FAILURE_DETECTION_n, checkingPumps(pumpStateMessages)));
      returnCheck = false;

      correctingPumps(pumpStateMessages);
      this.openOrClosedPumps[this.failPump] = false;
      outgoing.send(new Message(MessageKind.CLOSE_PUMP_n, this.failPump));
      if (this.numberOfActivePumps >= 1) {
        this.numberOfActivePumps--;
      }

      // checking if pump controller has any failures
    } else if (checkingPumps(pumpControlStateMessages) != -1) {
      this.mode = State.DEGRADED;
      this.failureMode = Failure.PUMP_CONTROL_STATE_FAIL;

      outgoing.send(new Message(Mailbox.MessageKind.MODE_m, Mode.DEGRADED));
      outgoing.send(new Message(MessageKind.PUMP_CONTROL_FAILURE_DETECTION_n,
          checkingPumps(pumpControlStateMessages)));

      storePumpFailure(checkingPumps(pumpControlStateMessages));
      returnCheck = false;
      correctingPumps(pumpControlStateMessages);

      // checking if steam level sensors has any failures
    } else if (this.currentSteamLevel < this.previousSteamLevel
        || this.currentSteamLevel > this.maximalSteamRate) {
      this.mode = State.DEGRADED;
      this.failureMode = Failure.STEAM_LEVEL_FAIL;

      outgoing.send(new Message(Mailbox.MessageKind.MODE_m, Mode.DEGRADED));
      outgoing.send(new Message(MessageKind.STEAM_FAILURE_DETECTION));
      returnCheck = false;
    }

    // checking if water level sensors has any failures
    if (this.initilization && ((this.waterLevel < this.minLimit && this.waterLevel > 0)
        || this.waterLevel > this.maxLimit)) {
      this.mode = State.EMERGENCY_STOP;

      outgoing.send(new Message(Mailbox.MessageKind.MODE_m, Mode.EMERGENCY_STOP));
      returnCheck = false;
    }
    if (this.waterLevel < 0 || this.waterLevel >= this.configuration.getCapacity()) {
      this.mode = State.RESCUE;
      this.failureMode = Failure.WATER_LEVEL_FAIL;

      outgoing.send(new Message(Mailbox.MessageKind.MODE_m, Mode.RESCUE));
      outgoing.send(new Message(MessageKind.LEVEL_FAILURE_DETECTION));
      returnCheck = false;
    }

    return returnCheck;

  }

  /**
   * Records the water levels for the current and previous pump cycles.
   * 
   * @param levelMessage : Messages about the water level in the steam boiler
   */
  private void waterLevelAssigning(Message levelMessage) {
    assert levelMessage != null;

    this.setPreviousWaterLevel(this.waterLevel);
    this.waterLevel = levelMessage.getDoubleParameter();
  }

  /**
   * Records the steam levels for the current and previous pump cycles.
   * 
   * @param steamMessage : Messages about the steam level in the steam boiler
   */
  private void steamLevelAssigning(Message steamMessage) {
    assert steamMessage != null;

    this.previousSteamLevel = this.currentSteamLevel;
    this.currentSteamLevel = steamMessage.getDoubleParameter();
  }

  /**
   * Check if sensors are reading a negative value, if so then the controller will
   * enter emergewncy stop mode.
   * 
   * @param levelMessage : Messages about the water level in the steam boiler
   * @param steamMessage : Messages about the steam level in the steam boiler
   */
  private void negativeSensorsCheck(Message levelMessage, Message steamMessage) {
    assert levelMessage != null;
    assert steamMessage != null;

    if (levelMessage.getDoubleParameter() < 0.0 && steamMessage.getDoubleParameter() < 0.0) {
      this.mode = State.EMERGENCY_STOP;
    }
  }

  /**
   * Makes sure that the sensors are not experiencing failures, otherwise the
   * steam boiler enters emergency stop.
   * 
   * @param levelMessage Messages regarding to the water levels
   * @param steamMessage Messages regarding to the steam levels
   * @return true
   */
  private boolean initializingSensorCheck(Message levelMessage, Message steamMessage) {
    assert levelMessage != null;
    assert steamMessage != null;

    if (steamMessage.getDoubleParameter() != 0.0 || levelMessage.getDoubleParameter() < 0.0
        || levelMessage.getDoubleParameter() > this.configuration.getCapacity()) {
      this.mode = State.EMERGENCY_STOP;
      return false;
    }
    return true;
  }

  /**
   * Controller check if each pump is correctly open or closed. If there is an
   * error the method will identify which pump is causing the error.
   * 
   * @param pumpMessage : Messages relating about the pump controller or the pump
   * @return true or false
   */
  private int checkingPumps(Message[] pumpMessage) {
    assert pumpMessage != null;

    for (int i = 0; i < this.numberOfPumps; i++) {
      if (this.openOrClosedPumps[i] != pumpMessage[i].getBooleanParameter()) {
        return i;
      }
    }
    return -1;
  }

  /**
   * The method makes sure that the each pump is correctly opened or closed.
   * 
   * @param pumpMessage : Messages relating about the pump controller or the pump
   */
  private void correctingPumps(Message[] pumpMessage) {
    assert pumpMessage != null;

    for (int i = 0; i < this.numberOfPumps; i++) {
      if (this.openOrClosedPumps[i] != pumpMessage[i].getBooleanParameter()) {
        this.openOrClosedPumps[i] = pumpMessage[i].getBooleanParameter();
      }
    }
    this.numberOfActivePumps = checkingActivePumps();
  }

  /**
   * Identifies how many pumps are on.
   * 
   * @return number of active pumps
   */
  private int checkingActivePumps() {
    int activePumps = 0;
    for (int i = 0; i < this.openOrClosedPumps.length; i++) {
      if (this.openOrClosedPumps[i] == true) {
        activePumps++;
      }
    }
    return activePumps;
  }

  /**
   * Check whether there was a transmission failure. This is indicated in several
   * ways. Firstly, when one of the required messages is missing. Secondly, when
   * the values returned in the messages are nonsensical.
   *
   * @param levelMessage      Extracted LEVEL_v message.
   * @param steamMessage      Extracted STEAM_v message.
   * @param pumpStates        Extracted PUMP_STATE_n_b messages.
   * @param pumpControlStates Extracted PUMP_CONTROL_STATE_n_b messages.
   * @return Whether transmission failed or not.
   */
  private boolean transmissionFailure(@Nullable Message levelMessage,
      @Nullable Message steamMessage, Message[] pumpStates, Message[] pumpControlStates) {
    // Check level readings
    if (levelMessage == null) {
      // Nonsense or missing level reading
      return true;
    } else if (steamMessage == null) {
      // Nonsense or missing steam reading
      return true;
    } else if (pumpStates.length != this.configuration.getNumberOfPumps()) {
      // Nonsense pump state readings
      return true;
    } else if (pumpControlStates.length != this.configuration.getNumberOfPumps()) {
      // Nonsense pump control state readings
      return true;
    }
    // Done
    return false;
  }

  /**
   * Find and extract a message of a given kind in a mailbox. This must the only
   * match in the mailbox, else <code>null</code> is returned.
   *
   * @param kind     The kind of message to look for.
   * @param incoming The mailbox to search through.
   * @return The matching message, or <code>null</code> if there was not exactly
   *         one match.
   */
  private static @Nullable Message extractOnlyMatch(MessageKind kind, Mailbox incoming) {
    assert kind != null;
    assert incoming != null;

    Message match = null;
    for (int i = 0; i != incoming.size(); ++i) {
      Message ith = incoming.read(i);
      if (ith.getKind() == kind) {
        if (match == null) {
          match = ith;
        } else {
          // This indicates that we matched more than one message of the given kind.
          return null;
        }
      }
    }
    return match;
  }

  /**
   * Find and extract all messages of a given kind.
   *
   * @param kind     The kind of message to look for.
   * @param incoming The mailbox to search through.
   * @return The array of matches, which can empty if there were none.
   */
  private static Message[] extractAllMatches(MessageKind kind, Mailbox incoming) {
    assert kind != null;
    assert incoming != null;

    int count = 0;
    // Count the number of matches
    for (int i = 0; i != incoming.size(); ++i) {
      Message ith = incoming.read(i);
      if (ith.getKind() == kind) {
        count = count + 1;
      }
    }
    // Now, construct resulting array
    Message[] matches = new Message[count];
    int index = 0;
    for (int i = 0; i != incoming.size(); ++i) {
      Message ith = incoming.read(i);
      if (ith.getKind() == kind) {
        matches[index++] = ith;
      }
    }
    return matches;
  }

  /**
   * Returns the maximum level the water level can reach.
   * 
   * @return The maximum level the water level can reach.
   */
  public double getMaxLimit() {
    return this.maxLimit;
  }

  /**
   * Returns the number of pumps that are opened.
   * 
   * @return The number of pumps that are opened.
   */
  public double getNumberofActivePumps() {
    return this.numberOfActivePumps;
  }

  /**
   * Returns the minimum level the water level can reach.
   * 
   * @return The minimum level the water level can reach.
   */
  public double getMinLimit() {
    return this.minLimit;
  }

  /**
   * Returns the maximum normal level the water level can reach.
   * 
   * @return The maximum normal level the water level can reach.
   */
  public double getMaxNormal() {
    return this.maxNormal;
  }

  /**
   * Returns the minimum normal level the water level can reach.
   * 
   * @return The minimum normal level the water level can reach.
   */
  public double getMinNormal() {
    return this.minNormal;
  }

  /**
   * Returns the current water level.
   * 
   * @return The current water level.
   */
  public double getWaterLevel() {
    return this.waterLevel;
  }

  /**
   * Sets the current water level.
   * 
   * @param waterLevel :The current water level.
   */
  public void setWaterLevel(double waterLevel) {
    this.waterLevel = waterLevel;
  }

  /**
   * Returns if valves had been open or not.
   * 
   * @return whether valve is open or not.
   */
  public boolean isValveOpen() {
    return this.valveOpen;
  }

  /**
   * Sets the value that represents whether valves had ben open.
   * 
   * @param valveOpen : The new boolean value to determines if valves are open or
   *                  not.
   */
  public void setValveOpen(boolean valveOpen) {
    this.valveOpen = valveOpen;
  }

  /**
   * Gets the array of each pumps open or closed status.
   * 
   * @return each pumps open or closed status
   */
  public boolean[] isOpenPump() {
    return this.openOrClosedPumps;
  }

  /**
   * Sets the array of each pumps open or closed status.
   * 
   * @param openPump : A new array of each pumps open or closed status
   */
  public void setOpenPump(boolean[] openPump) {
    this.openOrClosedPumps = openPump;
  }

  /**
   * Gets the predicted average water levels.
   * 
   * @return The predicted average water levels
   */
  public double[] getMidPointWaterLevel() {
    return this.predictedMidPointWaterLevels;
  }

  /**
   * Sets the predicted average water levels.
   * 
   * @param midPointWaterLevel : The new predicted average water levels
   */
  public void setMidPointWaterLevel(double[] midPointWaterLevel) {
    this.predictedMidPointWaterLevels = midPointWaterLevel;
  }

  /**
   * Gets the pump capacity for each pump.
   * 
   * @return the pump capacity for each pump
   */
  public double[] getPumpCapacity() {
    return this.pumpCapacity;
  }

  /**
   * Returns the maximum steam rate for the steam boiler.
   * 
   * @return the maximum steam rate for the steam boiler.
   */
  public double getMaximalSteamRate() {
    return this.maximalSteamRate;
  }

  /**
   * Gets the predicted maximum water levels.
   * 
   * @return The predicted maximum water levels
   */
  public double[] getPredictedMaxLitres() {
    return this.predictedMaxWaterLevels;
  }

  /**
   * Sets the predicted maximum water levels.
   * 
   * @param predictedMaxLitres : The new predicted maximum water levels
   */
  public void setPredictedMaxLitres(double[] predictedMaxLitres) {
    this.predictedMaxWaterLevels = predictedMaxLitres;
  }

  /**
   * Gets the predicted minimum water levels.
   * 
   * @return The predicted minimum water levels
   */
  public double[] getPredictedMinLitres() {
    return this.predictedMinWaterLevels;
  }

  /**
   * Sets the predicted minimum water levels.
   * 
   * @param predictedMinLitres : The new predicted minimum water levels
   */
  public void setPredictedMinLitres(double[] predictedMinLitres) {
    this.predictedMinWaterLevels = predictedMinLitres;
  }

  /**
   * Returns the ideal water level in the steam boiler.
   * 
   * @return The ideal water level in the steam boiler
   */
  public double getNormalMidPointWaterLevel() {
    return this.normalMidPointWaterLevel;
  }

  /**
   * Gets the ideal predicted water level.
   * 
   * @return the ideal predicted water level.
   */
  public double getIdealPredictedWater() {
    return this.idealPredictedWater;
  }

  /**
   * Sets the new ideal predicted water level.
   * 
   * @param idealPredictedWater : The new ideal predicted water level.
   */
  public void setIdealPredictedWater(double idealPredictedWater) {
    this.idealPredictedWater = idealPredictedWater;
  }

  /**
   * Return the current steam level for current pump cycle.
   * 
   * @return The steam level for the current pump cycle.
   */
  public double getCurrentSteamLevel() {
    return this.currentSteamLevel;
  }

  /**
   * Sets the steam level for the current pump cycle.
   * 
   * @param currentSteamLevel : The new steam level for the current pump cycle.
   */
  public void setCurrentSteamLevel(double currentSteamLevel) {
    this.currentSteamLevel = currentSteamLevel;
  }

  /**
   * Returns the steam level for the previous pump cycle.
   * 
   * @return The steam level for the previous pump cycle.
   */
  public double getPreviousSteamLevel() {
    return this.previousSteamLevel;
  }

  /**
   * Sets the steam level from the last pump cycle.
   * 
   * @param previousSteamLevel : The new steam level for the previous pump cycle.
   */
  public void setPreviousSteamLevel(double previousSteamLevel) {
    this.previousSteamLevel = previousSteamLevel;
  }

  /**
   * Returns the ideal predicted water level from the previous pump cycle.
   * 
   * @return The ideal predicted water level from the previous pump cycle.
   */
  public double getPreviousidealPredictedWater() {
    return this.previousidealPredictedWater;
  }

  /**
   * Sets the ideal predicted water level from the previous pump cycle.
   * 
   * @param previousidealPredictedWater : The new ideal predicted water level from
   *                                    the previous pump cycle.
   */
  public void setPreviousidealPredictedWater(double previousidealPredictedWater) {
    this.previousidealPredictedWater = previousidealPredictedWater;
  }

  /**
   * Returns the water level from the previous pump cycle.
   * 
   * @return The water level from the previous cycle
   */
  public double getPreviousWaterLevel() {
    return this.previousWaterLevel;
  }

  /**
   * Sets the water level from the previous cycle.
   * 
   * @param previousWaterLevel :The new water level for the previous cycle
   */
  public void setPreviousWaterLevel(double previousWaterLevel) {
    this.previousWaterLevel = previousWaterLevel;
  }

  /**
   * Gets the failuremode the conntroller is on controller is on currently.
   * 
   * @return Which of failuremode the controller is on
   */
  public Failure getFailureMode() {
    return this.failureMode;
  }

  /**
   * Sets which failuremode the controller is on.
   * 
   * @param failureMode :The controllers next failuremode state
   */
  public void setFailureMode(Failure failureMode) {
    this.failureMode = failureMode;
  }

  /**
   * Gets the value that represent the failing pump.
   * 
   * @return The value that represent the failing pump.
   */
  public int getFailPump() {
    return this.failPump;
  }

  /**
   * Sets the value that represent the failing pump.
   * 
   * @param failPump :The new failing pump value.
   */
  public void setFailPump(int failPump) {
    this.failPump = failPump;
  }
}
