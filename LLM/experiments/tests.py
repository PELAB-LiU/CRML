import textwrap
from types import SimpleNamespace
import json
import os
import sys
from experiments.util import AttrDict

#TEXT2VQL_ROOT = os.path.abspath(os.path.dirname(__file__))
#
#while True:
#    if os.path.basename(TEXT2VQL_ROOT) == "Text2VQL":
#        sys.path.append(os.path.join(TEXT2VQL_ROOT, "dataset_construction"))
#        break
#    new = os.path.dirname(TEXT2VQL_ROOT)
#    if new == TEXT2VQL_ROOT:
#        raise FileNotFoundError("Could not find a parent directory named 'Text2VQL'.")
#    TEXT2VQL_ROOT = new

#
# Structure
# References:
#   * https://github.com/OpenModelica/CRML/blob/main/resources/use_cases/cooling_system/ics_reqs.crml
TESTS = AttrDict({
    "SRI": {
        "temp": {
            "seed": textwrap.dedent(
                        """\
                        model SRI is FORM_L union {
                            Real T is external;
                        };"""),
            "interactions":[
                {
                    "req": {
                        "nl": {
                            "en": "The temperature of the SRI must be maintained between 16°C and 30°C.",
                            "fr": "La température du SRI doit être maintenue entre 16°C et 30°C."
                        },
                        "snl": {
                            "en": "In normal operation, the SRI system shall have a temperature between 16°C and 30°C.",
                            "fr": "En fonctionnement normal, le système SRI doit avoir une température entre 16°C et 30°C."
                        }
                    }
                },
                {
                    "req": {
                        "nl": {
                            "en": "If the temperature exceeds these limits, it must return to the acceptable range within one minute.",
                            "fr": "Si la température dépasse ces limites, elle doit être revenue dans l'intervalle au bout d'une minute."
                        },
                        "snl": {
                            "en": "When the SRI system temperature exceeds the normal operating limits, the SRI system temperature shall return to the normal operating range within one minute.",
                            "fr": "Lorsque le système SRI a une température qui dépasse les limites du fonctionnement normal, le système SRI doit avoir une température à nouveau dans l'intervalle autorisé en fonctionnement normal au bout d'une minute."
                        }
                    }
                }
            ]
        },
        "speed": {
            "seed": textwrap.dedent(
                        """\
                        model ics_reqs is {
                            class Req_speed is {
		                        Real v is external;
	                        };
                        };"""),
            "interactions":[
                {
                    "req": {
                        "nl": {
                            "en": "To limit erosion and corrosion phenomena, the fluid velocity in the heat exchangers must not exceed 6 m/s.",
                            "fr": "Afin de limiter les phénomènes d'érosion et de corrosion, la vitesse du fluide dans les échangeurs ne doit pas dépasser 6 m/s."
                        },
                        "snl": {
                            "en": "To limit erosion and corrosion phenomena, the fluid velocity in the heat exchangers shall not exceed 6 m/s.",
                            "fr": "Afin de limiter les phénomènes d'érosion et de corrosion, la vitesse du fluide dans les échangeurs ne doit pas dépasser 6 m/s."
                        }
                    }
                }
            ]
        }
    },
    # https://github.com/PELAB-LiU/CRML/blob/upstream/resources/crml_tutorial/traffic_light/TrafficLightSpecification_articleModelicaConf2025.crml
    "traffic": {
        "t1": {            
            "seed": textwrap.dedent(
                        """\
                        model TrafficLightSpecification_article is flatten {ETL, FORM_L}
                            // Import of libraries
                            
                            union {
                                // List of external variables
                                Boolean red is external;
                                Boolean yellow is external;
                                Boolean green is external;
 
                                Boolean operation is external;
                                Boolean night_mode is external;
                                Boolean day_mode is not night_mode;
                        };"""),
            "interactions": [
                {
                    "req": {
                        "en": "During operation, no more than one light should be on (a flashing mode could be specified later)."
                    }
                },
                {
                    "req": {
                        "en": "During day, after green, next step is yellow."
                    }
                },
                {
                    "req": {
                        "en": "During day, step green should stay active for at least 30 seconds."
                    }
                },
                {
                    "req": {
                        "en": "During day, after green becomes active + 30 seconds, next step should turn yellow within 0.2 seconds."
                    }
                },
                {
                    "req": {
                        "en": "During night, yellow should only be used."
                    }
                },
                {
                    "req": {
                        "en": "During night, yellow should flash every 2 seconds."
                    }
                }
            ]
        }
    },
    # https://github.com/PELAB-LiU/CRML/blob/upstream/resources/crml_tutorial/pumping_system/pumping_system.crml
    "pumpsystem": {
        "t1": {            
            "seed": textwrap.dedent(
                        """\
                        model PumpingSystem is flatten {Units, FORM_L}
	                        
	                        union {
		                        // type Requirement is Boolean forbid { *, +, integrate };
		                        
		                        class Pump is {
			                        Boolean isStarted is external;
			                        Real temperature is external;
                        			
		                        };
                        		
		                        class System is {
			                        Boolean inOperation is external;
		                        };
		                        Pump p1;
                                Pump p2;
                                Pump p3;

                                System system;

		                        //System system( pumps = {Pump (ident = "PO1"),  Pump (ident = "PO2"), Pump (ident = "PO3")} );
                        };"""),
            "interactions": [
                {
                    "req": {
                        "en": "R1: While the system is in operation, the pump must not be started more than twice."
                    }
                },
                {
                    "req": {
                        "en": "R2: At least one hour must separate two consecutive pump startups."
                    }
                },
                {
                    "req": {
                        "en": "R3: While the pump is in operation (i.e. started), its temperature must always stay below 50°C."
                    }
                },
                {
                    "req": {
                        "en": "R4: While the system is in operation, after the pump temperature rises above 40 °C, the temperature must not stay above for a duration of more than 1 mn cumulated over the next 15 mn."
                    }
                }
            ]
        }
    }
})