package crml.language;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import crml.language.util.Parser;

public class SimpleParseTest {
    String model = """
model TestModel is {
    partial type Quantity is (Real q is rate*u + offset)  {  
        String SIUnit; // SI unit for quantity q 
        String userUnit; // User unit for quantity q 
        Real u; // Quantity q expressed in user units 
        Real rate; // Conversion rate between user units and SI units 
        Real offset; // offset between user units and SI units 
    }; 
    //Quantity x = Quantity (SIUnit = "Pa", userUnit = "bar", rate = 1.e5, offset = 0, u = 3); 
}
""";


String model2 = """
/* ============================================================
   CRML Aviation Model – Flight Management System (FMS)
   Domain   : Aviation / Cyber-Physical Systems
   Version  : 1.1  (corrected)
   Spec ref : CRML specification v1.2 (EDF R&D)

   Classes (4):
     1. AviationComponent        – abstract base (partial)
     2. Engine                   – turbofan engine
     3. AutoPilot                – autopilot control unit
     4. FlightManagementSystem   – top-level twin-engine FMS

   Requirements (4 total, >= 2 required):
     R1_temp     (Engine)    – EGT within safe thermal limits
     R2_thrust   (Engine)    – non-negative thrust while running
     R3_altitude (AutoPilot) – altitude deviation within envelope
     R4_global   (FMS)       – conjunction of all sub-requirements
   ============================================================ */

model AviationFMS is {

  /* ----------------------------------------------------------
     Type definitions (must live inside the model block)
  ---------------------------------------------------------- */

  /* Dedicated Requirement type: a Boolean whose temporal-
     arithmetic operators are forbidden (spec §4.21 pattern)   */
  type Requirement is Boolean forbid { *, +, integrate };

  /* Physical-quantity base type (spec §4.17 extension pattern) */
  partial type Quantity is (Real q is rate * u + offset) {
    String SIUnit;
    String userUnit;
    Real   u;       // quantity expressed in user units
    Real   rate;    // conversion factor  user-unit -> SI
    Real   offset;  // additive offset    user-unit -> SI
  };

  /* Temperature: SI = Kelvin; user unit = degrees Celsius      */
  partial type Temperature is Quantity (SIUnit is "K");
  type TemperatureCelsius is Temperature
    (userUnit is "degC", rate is 1.0, offset is 273.15) alias degC;

  /* Thrust:  SI = Newton;  user unit = kilo-Newton             */
  partial type Force is Quantity (SIUnit is "N");
  type ThrustKN is Force
    (userUnit is "kN", rate is 1000.0, offset is 0.0) alias kN;

  /* ----------------------------------------------------------
     Utility operator: open a time period while b is true
     (FORM-L §6.1 "during" pattern)
  ---------------------------------------------------------- */
  Operator [ Periods ] during Boolean b is
    Periods [ new Clock b, new Clock not b ];

  /* Absolute value for Real numbers                            */
  Operator [ Real ] abs [ Real ] x is if x >= 0.0 then x else -x;

  /* ----------------------------------------------------------
     CLASS 1 – AviationComponent  (abstract base)
     Provides a unique identifier and the inOperation flag
     shared by all FMS components.
  ---------------------------------------------------------- */
  partial class AviationComponent is {
    String  id;
    Boolean inOperation is external;
  };

  /* ----------------------------------------------------------
     CLASS 2 – Engine
     Models a single turbofan engine.

     External signals:
       thrust   – net thrust [kN]
       temp_egt – Exhaust Gas Temperature [degC]

     Requirements:
       R1_temp   – EGT must stay in [200 degC, 950 degC] while running
       R2_thrust – thrust must be >= 0 kN while running
  ---------------------------------------------------------- */
  class Engine is {
    ThrustKN    thrust   is external;
    Temperature temp_egt is external;

    /* R1 – Thermal envelope: EGT between 200 degC and 950 degC
             throughout the entire running period              */
    Requirement R1_temp is
      during inOperation ensure
        (temp_egt >= 200 degC) and (temp_egt <= 950 degC);

    /* R2 – Non-negative thrust while the engine is running   */
    Requirement R2_thrust is
      during inOperation ensure thrust >= 0 kN;

  } extends AviationComponent;

  /* ----------------------------------------------------------
     CLASS 3 – AutoPilot
     Models the autopilot control unit.

     External signals:
       engaged       – true when AP is controlling the aircraft
       alt_deviation – signed deviation from cleared altitude [m]
       nav_valid     – true when navigation data feed is valid

     Requirement:
       R3_altitude – |alt_deviation| <= 30 m whenever the AP is
                     engaged with a valid navigation source
  ---------------------------------------------------------- */
  class AutoPilot is {
    Boolean engaged       is external;
    Real    alt_deviation is external;
    Boolean nav_valid     is external;

    /* R3 – Altitude containment: +/- 30 m of cleared level   */
    Requirement R3_altitude is
      during (engaged and nav_valid) ensure
        (abs alt_deviation) <= 30.0;

  } extends AviationComponent;

  /* ----------------------------------------------------------
     CLASS 4 – FlightManagementSystem
     Top-level system aggregating two engines and one autopilot.

     Requirement:
       R4_global – system oracle: conjunction of all
                   sub-component requirements
  ---------------------------------------------------------- */
  class FlightManagementSystem is {
    Engine    engine1;
    Engine    engine2;
    AutoPilot autopilot;

    /* R4 – Global oracle                                      */
    Requirement R4_global is
      engine1.R1_temp    and
      engine1.R2_thrust  and
      engine2.R1_temp    and
      engine2.R2_thrust  and
      autopilot.R3_altitude;

  } extends AviationComponent;

  /* ----------------------------------------------------------
     Model instantiation – a concrete twin-engine FMS
  ---------------------------------------------------------- */
  FlightManagementSystem FMS_A320 is new FlightManagementSystem (
    id        is "FMS_A320",
    engine1   is Engine    (id is "ENG_PORT"),
    engine2   is Engine    (id is "ENG_STBD"),
    autopilot is AutoPilot (id is "AP_PRIMARY")
  );

}; /* end model AviationFMS */
""";

    @Test
    public void test(){
        Parser parser = new Parser();
        var result = parser.parse(model);

        result.syntax().errors().forEach(System.out::println);

        assertFalse(result.syntax().hasErrors());
    }    
}
