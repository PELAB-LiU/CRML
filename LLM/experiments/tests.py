import textwrap
from experiments.util import AttrDict

# Each domain has one seed model and a flat list of requirements generated individually.
# References:
#   SRI:         https://github.com/OpenModelica/CRML/blob/main/resources/use_cases/cooling_system/ics_reqs.crml
#   Traffic:     https://github.com/PELAB-LiU/CRML/blob/upstream/resources/crml_tutorial/traffic_light/TrafficLightSpecification_articleModelicaConf2025.crml
#   Pump system: https://github.com/PELAB-LiU/CRML/blob/upstream/resources/crml_tutorial/pumping_system/pumping_system.crml
TESTS = AttrDict({
    "SRI": {
        "seed": textwrap.dedent(
            """\
            model SRI is FORM_L union {
                Real T is external;
                class Req_speed is {
                    Real v is external;
                };
                Req_speed Req_speed1;
                Req_speed Req_speed2;
            };"""),
        "requirements": [
            {
                "id": "temp1",
                "en": "In normal operation, the SRI system shall have a temperature between 16°C and 30°C.",
            },
            {
                "id": "temp2",
                "en": "When the SRI system temperature exceeds the normal operating limits, the SRI system temperature shall return to the normal operating range within one minute. (In normal operation, the SRI system shall have a temperature between 16°C and 30°C.)",
            },
            {
                "id": "speed1",
                "en": "To limit erosion and corrosion phenomena, the fluid velocity in the heat exchangers shall not exceed 6 m/s.",
            },
        ],
    },
    "traffic": {
        "seed": textwrap.dedent(
            """\
            model TrafficLightSpecification_article is flatten {ETL, FORM_L}

            union {
                Boolean red is external;
                Boolean yellow is external;
                Boolean green is external;

                Boolean operation is external;
                Boolean night_mode is external;
                Boolean day_mode is not night_mode;
            };"""),
        "requirements": [
            {
                "id": "r1",
                "en": "During operation, no more than one light should be on (a flashing mode could be specified later).",
            },
            {
                "id": "r2",
                "en": "During day, after green, next step is yellow.",
            },
            {
                "id": "r3",
                "en": "During day, step green should stay active for at least 30 seconds.",
            },
            {
                "id": "r4",
                "en": "During day, after green becomes active + 30 seconds, next step should turn yellow within 0.2 seconds.",
            },
            {
                "id": "r5",
                "en": "During night, yellow should only be used.",
            },
            {
                "id": "r6",
                "en": "During night, yellow should flash every 2 seconds.",
            },
        ],
    },
    "pumpsystem": {
        "seed": textwrap.dedent(
            """\
            model PumpingSystem is FORM_L

            union {

                class Pump is {
                    Boolean systemInOperation is external;
                    Boolean isStarted is external;
                    Real temperature is external;
                };

                Boolean inOperation is external;
                Pump pump1(systemInOperation=inOperation);
                Pump pump2(systemInOperation=inOperation);
                Pump pump3(systemInOperation=inOperation);
            };"""),
        "requirements": [
            {
                "id": "r1",
                "en": "R1: While the system is in operation, the pump must not be started more than twice.",
            },
            {
                "id": "r2",
                "en": "R2: At least one hour must separate two consecutive pump startups.",
            },
            {
                "id": "r3",
                "en": "R3: While the pump is in operation (i.e. started), its temperature must always stay below 50°C.",
            },
            {
                "id": "r4",
                "en": "R4: While the system is in operation, after the pump temperature rises above 40 °C, the temperature must not stay above for a duration of more than 1 mn cumulated over the next 15 mn.",
            },
        ],
    },
})
