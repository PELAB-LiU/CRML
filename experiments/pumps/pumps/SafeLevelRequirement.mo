within pumps;
model SafeLevelRequirement 
model TankModel
Real waterLevel;
Real maxVolume;
CRMLtoModelica.Types.Boolean4 levelSafe = CRMLtoModelica.Functions.cvBooleanToBoolean4(waterLevel < 0.8 * maxVolume);
end TankModel; 
TankModel tank1;
TankModel tank2;
CRMLtoModelica.Types.Boolean4 tanksAreSafe = CRMLtoModelica.Functions.and4(tank2.levelSafe, tank1.levelSafe);
end SafeLevelRequirement;

