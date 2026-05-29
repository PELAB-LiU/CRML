within sri2_ref;
model ics_reqs 
model 'or'
CRMLtoModelica.Types.Boolean4 b1;
CRMLtoModelica.Types.Boolean4 b2;
CRMLtoModelica.Types.Boolean4 out; 

equation 
 out =CRMLtoModelica.Functions.not4( CRMLtoModelica.Functions.and4(CRMLtoModelica.Functions.not4( b2), CRMLtoModelica.Functions.not4( b1)));
end 'or';
model 'xor'
CRMLtoModelica.Types.Boolean4 b1;
CRMLtoModelica.Types.Boolean4 b2;
CRMLtoModelica.Types.Boolean4 out; 
'or' 'or0'(b2=b2, b1=b1);

equation 
 out =CRMLtoModelica.Functions.and4(CRMLtoModelica.Functions.not4( CRMLtoModelica.Functions.and4(b2, b1)), 'or0'.out);
end 'xor';
model 'implies'
CRMLtoModelica.Types.Boolean4 b1;
CRMLtoModelica.Types.Boolean4 b2;
CRMLtoModelica.Types.Boolean4 out; 
'or' 'or1'(b2=b2, b1=CRMLtoModelica.Functions.not4( b1));

equation 
 out ='or1'.out;
end 'implies';
model 'inside'
CRMLtoModelica.Types.CRMLClock out; 
CRMLtoModelica.Types.CRMLClock C;
CRMLtoModelica.Types.CRMLPeriod P;
CRMLtoModelica.Blocks.ClockTick CRMLtoModelica_Blocks_ClockTick2(r1 = C);
CRMLtoModelica.Blocks.ClockTick CRMLtoModelica_Blocks_ClockTick3(r1 = C);
CRMLtoModelica.Blocks.EventFilter CRMLtoModelica_Blocks_EventFilter4(r1 = C,r2 = CRMLtoModelica.Functions.and4(CRMLtoModelica.Functions.lEV(CRMLtoModelica.Functions.PEnd(P), CRMLtoModelica_Blocks_ClockTick3.out), CRMLtoModelica.Functions.gEV(CRMLtoModelica.Functions.PStart(P), CRMLtoModelica_Blocks_ClockTick2.out)));

equation 
 out =CRMLtoModelica_Blocks_EventFilter4.out;
end 'inside';
model 'countinside'
Integer out; 
CRMLtoModelica.Types.CRMLClock C;
CRMLtoModelica.Types.CRMLPeriod P;
'inside' 'inside5'(P=P, C=C);
CRMLtoModelica.Blocks.CardClock CRMLtoModelica_Blocks_CardClock6(r1 = 'inside5'.out);

equation 
 out =CRMLtoModelica_Blocks_CardClock6.out;
end 'countinside';
model 'becomes true'
CRMLtoModelica.Types.CRMLClock out; 
CRMLtoModelica.Types.Boolean4 b;
CRMLtoModelica.Types.CRMLClock c7(b=b);
CRMLtoModelica.Types.CRMLClock_build c7_init(clock =c7);

equation 
 out =c7;
end 'becomes true';
model 'becomes false'
CRMLtoModelica.Types.CRMLClock out; 
CRMLtoModelica.Types.Boolean4 b;
'becomes true' 'becomes true8'(b=CRMLtoModelica.Functions.not4( b));

equation 
 out ='becomes true8'.out;
end 'becomes false';
model 'becomes true inside'
CRMLtoModelica.Types.CRMLClock out; 
CRMLtoModelica.Types.Boolean4 b;
CRMLtoModelica.Types.CRMLPeriod P;
'becomes true' 'becomes true9'(b=b);
'inside' 'inside9'(P=P, C='becomes true9'.out);

equation 
 out ='inside9'.out;
end 'becomes true inside';
model 'becomes false inside'
CRMLtoModelica.Types.CRMLClock out; 
CRMLtoModelica.Types.Boolean4 b;
CRMLtoModelica.Types.CRMLPeriod P;
'becomes false' 'becomes false11'(b=b);
'inside' 'inside11'(P=P, C='becomes false11'.out);

equation 
 out ='inside11'.out;
end 'becomes false inside';
model 'decideover'
CRMLtoModelica.Types.Boolean4 out; 
CRMLtoModelica.Types.Boolean4 phi;
CRMLtoModelica.Types.CRMLPeriod P;
'or' 'or13'(b2=CRMLtoModelica.Functions.Event2Boolean( CRMLtoModelica.Functions.PEnd(P)), b1=phi);

equation 
 out ='or13'.out;
end 'decideover';
model 'evaluateover'
CRMLtoModelica.Types.Boolean4 out; 
CRMLtoModelica.Types.Boolean4 phi;
CRMLtoModelica.Types.CRMLPeriod P;
'decideover' 'decideover14'(P=P, phi=phi);
CRMLtoModelica.Blocks.Integrate CRMLtoModelica_Blocks_Integrate15(r1 = CRMLtoModelica.Functions.mul4(phi, 'decideover14'.out),r2 = P);

equation 
 out =CRMLtoModelica_Blocks_Integrate15.out;
end 'evaluateover';
model 'checkover'
CRMLtoModelica.Types.Boolean4 out; 
CRMLtoModelica.Types.Boolean4 phi;
CRMLtoModelica.Types.CRMLPeriods P;
'evaluateover' 'evaluateover16'(P=P, phi=phi);
CRMLtoModelica.Blocks.unaryBoolAnd CRMLtoModelica_Blocks_unaryBoolAnd17(r1 = 'evaluateover16'.out);

equation 
 out =CRMLtoModelica_Blocks_unaryBoolAnd17.out;
end 'checkover';
model '>_int'
CRMLtoModelica.Types.Boolean4 out; 
Integer x;
Integer n;

equation 
 out =CRMLtoModelica.Functions.cvBooleanToBoolean4(x > n);
end '>_int';
model '>=_int'
CRMLtoModelica.Types.Boolean4 out; 
Integer x;
Integer n;

equation 
 out =CRMLtoModelica.Functions.cvBooleanToBoolean4(x >= n);
end '>=_int';
model '<_int'
CRMLtoModelica.Types.Boolean4 out; 
Integer x;
Integer n;

equation 
 out =CRMLtoModelica.Functions.cvBooleanToBoolean4(x < n);
end '<_int';
model '<=_int'
CRMLtoModelica.Types.Boolean4 out; 
Integer x;
Integer n;

equation 
 out =CRMLtoModelica.Functions.cvBooleanToBoolean4(x <= n);
end '<=_int';
model '==_int'
CRMLtoModelica.Types.Boolean4 out; 
Integer x;
Integer n;

equation 
 out =CRMLtoModelica.Functions.cvBooleanToBoolean4(x == n);
end '==_int';
model '<>_int'
CRMLtoModelica.Types.Boolean4 out; 
Integer x;
Integer n;

equation 
 out =CRMLtoModelica.Functions.cvBooleanToBoolean4(x <> n);
end '<>_int';
model '>_real'
CRMLtoModelica.Types.Boolean4 out; 
Real x;
Real d;

equation 
 out =CRMLtoModelica.Functions.cvBooleanToBoolean4(x > d);
end '>_real';
model '>=_real'
CRMLtoModelica.Types.Boolean4 out; 
Real x;
Real d;

equation 
 out =CRMLtoModelica.Functions.cvBooleanToBoolean4(x >= d);
end '>=_real';
model '<_real'
CRMLtoModelica.Types.Boolean4 out; 
Real x;
Real d;

equation 
 out =CRMLtoModelica.Functions.cvBooleanToBoolean4(x < d);
end '<_real';
model '<=_real'
CRMLtoModelica.Types.Boolean4 out; 
Real x;
Real d;

equation 
 out =CRMLtoModelica.Functions.cvBooleanToBoolean4(x <= d);
end '<=_real';
model 'id'
CRMLtoModelica.Types.Boolean4 out; 
CRMLtoModelica.Types.Boolean4 b;

equation 
 out =b;
end 'id';
model 'cte_false'
CRMLtoModelica.Types.Boolean4 out; 
CRMLtoModelica.Types.Boolean4 b;

equation 
 out =CRMLtoModelica.Types.Boolean4.false4;
end 'cte_false';
model 'cte_true'
CRMLtoModelica.Types.Boolean4 out; 
CRMLtoModelica.Types.Boolean4 b;

equation 
 out =CRMLtoModelica.Types.Boolean4.true4;
end 'cte_true';
model 'set to false'
CRMLtoModelica.Types.Boolean4 out; 
CRMLtoModelica.Types.Boolean4 b;
'id' 'id18'(b=b);

equation 
 out ='id18'.out;
end 'set to false';
model 'set to true'
CRMLtoModelica.Types.Boolean4 out; 
CRMLtoModelica.Types.Boolean4 b;
'id' 'id19'(b=b);

equation 
 out ='id19'.out;
end 'set to true';
model 'from'
CRMLtoModelica.Types.CRMLPeriods out; 
CRMLtoModelica.Types.CRMLClock ev;
CRMLtoModelica.Types.CRMLClock c21(b=CRMLtoModelica.Types.Boolean4.false4);
CRMLtoModelica.Types.CRMLClock_build c21_init(clock =c21);
CRMLtoModelica.Types.CRMLPeriods ps20(isLeftBoundaryIncluded=true, isRightBoundaryIncluded=true, start_event=ev, close_event=c21);
CRMLtoModelica.Types.CRMLPeriods_build ps20_init(ps =ps20);

equation 
 out =ps20;
end 'from';
model 'after'
CRMLtoModelica.Types.CRMLPeriods out; 
CRMLtoModelica.Types.CRMLClock ev;
CRMLtoModelica.Types.CRMLClock c23(b=CRMLtoModelica.Types.Boolean4.false4);
CRMLtoModelica.Types.CRMLClock_build c23_init(clock =c23);
CRMLtoModelica.Types.CRMLPeriods ps22(isLeftBoundaryIncluded=false, isRightBoundaryIncluded=true, start_event=ev, close_event=c23);
CRMLtoModelica.Types.CRMLPeriods_build ps22_init(ps =ps22);

equation 
 out =ps22;
end 'after';
model 'before'
CRMLtoModelica.Types.CRMLPeriods out; 
CRMLtoModelica.Types.CRMLClock ev;
CRMLtoModelica.Types.CRMLClock c25(b=CRMLtoModelica.Types.Boolean4.false4);
CRMLtoModelica.Types.CRMLClock_build c25_init(clock =c25);
CRMLtoModelica.Types.CRMLPeriods ps24(isLeftBoundaryIncluded=true, isRightBoundaryIncluded=false, start_event=c25, close_event=ev);
CRMLtoModelica.Types.CRMLPeriods_build ps24_init(ps =ps24);

equation 
 out =ps24;
end 'before';
model 'until'
CRMLtoModelica.Types.CRMLPeriods out; 
CRMLtoModelica.Types.CRMLClock ev;
CRMLtoModelica.Types.CRMLClock c27(b=CRMLtoModelica.Types.Boolean4.false4);
CRMLtoModelica.Types.CRMLClock_build c27_init(clock =c27);
CRMLtoModelica.Types.CRMLPeriods ps26(isLeftBoundaryIncluded=true, isRightBoundaryIncluded=true, start_event=c27, close_event=ev);
CRMLtoModelica.Types.CRMLPeriods_build ps26_init(ps =ps26);

equation 
 out =ps26;
end 'until';
model 'during'
CRMLtoModelica.Types.CRMLPeriods out; 
CRMLtoModelica.Types.Boolean4 b;
CRMLtoModelica.Types.CRMLClock c29(b=b);
CRMLtoModelica.Types.CRMLClock_build c29_init(clock =c29);
CRMLtoModelica.Types.CRMLClock c30(b=CRMLtoModelica.Functions.not4( b));
CRMLtoModelica.Types.CRMLClock_build c30_init(clock =c30);
CRMLtoModelica.Types.CRMLPeriods ps28(isLeftBoundaryIncluded=true, isRightBoundaryIncluded=true, start_event=c29, close_event=c30);
CRMLtoModelica.Types.CRMLPeriods_build ps28_init(ps =ps28);

equation 
 out =ps28;
end 'during';
model 'afterbefore'
CRMLtoModelica.Types.CRMLPeriods out; 
CRMLtoModelica.Types.CRMLClock ev1;
CRMLtoModelica.Types.CRMLClock ev2;
CRMLtoModelica.Types.CRMLPeriods ps31(isLeftBoundaryIncluded=false, isRightBoundaryIncluded=false, start_event=ev1, close_event=ev2);
CRMLtoModelica.Types.CRMLPeriods_build ps31_init(ps =ps31);

equation 
 out =ps31;
end 'afterbefore';
model 'afteruntil'
CRMLtoModelica.Types.CRMLPeriods out; 
CRMLtoModelica.Types.CRMLClock ev1;
CRMLtoModelica.Types.CRMLClock ev2;
CRMLtoModelica.Types.CRMLPeriods ps32(isLeftBoundaryIncluded=false, isRightBoundaryIncluded=true, start_event=ev1, close_event=ev2);
CRMLtoModelica.Types.CRMLPeriods_build ps32_init(ps =ps32);

equation 
 out =ps32;
end 'afteruntil';
model 'afterfor'
CRMLtoModelica.Types.CRMLPeriods out; 
CRMLtoModelica.Types.CRMLClock ev;
Real d;
CRMLtoModelica.Blocks.ClockAdd CRMLtoModelica_Blocks_ClockAdd34(r1 = ev,r2 = d);
CRMLtoModelica.Types.CRMLPeriods ps33(isLeftBoundaryIncluded=false, isRightBoundaryIncluded=true, start_event=ev, close_event=CRMLtoModelica_Blocks_ClockAdd34.out);
CRMLtoModelica.Types.CRMLPeriods_build ps33_init(ps =ps33);

equation 
 out =ps33;
end 'afterfor';
model 'afterwithin'
CRMLtoModelica.Types.CRMLPeriods out; 
CRMLtoModelica.Types.CRMLClock ev;
Real d;
CRMLtoModelica.Blocks.ClockAdd CRMLtoModelica_Blocks_ClockAdd36(r1 = ev,r2 = d);
CRMLtoModelica.Types.CRMLPeriods ps35(isLeftBoundaryIncluded=false, isRightBoundaryIncluded=false, start_event=ev, close_event=CRMLtoModelica_Blocks_ClockAdd36.out);
CRMLtoModelica.Types.CRMLPeriods_build ps35_init(ps =ps35);

equation 
 out =ps35;
end 'afterwithin';
model 'frombefore'
CRMLtoModelica.Types.CRMLPeriods out; 
CRMLtoModelica.Types.CRMLClock ev1;
CRMLtoModelica.Types.CRMLClock ev2;
CRMLtoModelica.Types.CRMLPeriods ps37(isLeftBoundaryIncluded=true, isRightBoundaryIncluded=false, start_event=ev1, close_event=ev2);
CRMLtoModelica.Types.CRMLPeriods_build ps37_init(ps =ps37);

equation 
 out =ps37;
end 'frombefore';
model 'fromuntil'
CRMLtoModelica.Types.CRMLPeriods out; 
CRMLtoModelica.Types.CRMLClock ev1;
CRMLtoModelica.Types.CRMLClock ev2;
CRMLtoModelica.Types.CRMLPeriods ps38(isLeftBoundaryIncluded=true, isRightBoundaryIncluded=true, start_event=ev1, close_event=ev2);
CRMLtoModelica.Types.CRMLPeriods_build ps38_init(ps =ps38);

equation 
 out =ps38;
end 'fromuntil';
model 'fromfor'
CRMLtoModelica.Types.CRMLPeriods out; 
CRMLtoModelica.Types.CRMLClock ev;
Real d;
CRMLtoModelica.Blocks.ClockAdd CRMLtoModelica_Blocks_ClockAdd40(r1 = ev,r2 = d);
CRMLtoModelica.Types.CRMLPeriods ps39(isLeftBoundaryIncluded=true, isRightBoundaryIncluded=true, start_event=ev, close_event=CRMLtoModelica_Blocks_ClockAdd40.out);
CRMLtoModelica.Types.CRMLPeriods_build ps39_init(ps =ps39);

equation 
 out =ps39;
end 'fromfor';
model 'fromwithin'
CRMLtoModelica.Types.CRMLPeriods out; 
CRMLtoModelica.Types.CRMLClock ev;
Real d;
CRMLtoModelica.Blocks.ClockAdd CRMLtoModelica_Blocks_ClockAdd42(r1 = ev,r2 = d);
CRMLtoModelica.Types.CRMLPeriods ps41(isLeftBoundaryIncluded=true, isRightBoundaryIncluded=false, start_event=ev, close_event=CRMLtoModelica_Blocks_ClockAdd42.out);
CRMLtoModelica.Types.CRMLPeriods_build ps41_init(ps =ps41);

equation 
 out =ps41;
end 'fromwithin';
model 'when'
CRMLtoModelica.Types.CRMLPeriods out; 
CRMLtoModelica.Types.CRMLClock ev;
CRMLtoModelica.Types.CRMLPeriods ps43(isLeftBoundaryIncluded=true, isRightBoundaryIncluded=true, start_event=ev, close_event=ev);
CRMLtoModelica.Types.CRMLPeriods_build ps43_init(ps =ps43);

equation 
 out =ps43;
end 'when';
model 'check at end'
CRMLtoModelica.Types.Boolean4 out; 
CRMLtoModelica.Types.CRMLPeriods P;
CRMLtoModelica.Types.Boolean4 b;
'set to false' 'set to false44'(b=b);
'checkover' 'checkover44'(P=P, phi='set to false44'.out);

equation 
 out ='checkover44'.out;
end 'check at end';
model 'check anytime'
CRMLtoModelica.Types.Boolean4 out; 
CRMLtoModelica.Types.CRMLPeriods P;
CRMLtoModelica.Types.Boolean4 b;
'set to true' 'set to true46'(b=b);
'checkover' 'checkover46'(P=P, phi='set to true46'.out);

equation 
 out ='checkover46'.out;
end 'check anytime';
model 'ensure'
CRMLtoModelica.Types.Boolean4 out; 
CRMLtoModelica.Types.CRMLPeriods P;
CRMLtoModelica.Types.Boolean4 b;
'check anytime' 'check anytime48'(b=b, P=P);

equation 
 out ='check anytime48'.out;
end 'ensure';
CRMLtoModelica.Types.Boolean4 normal_operation;
Real T;
CRMLtoModelica.Types.Boolean4 T_in_range = CRMLtoModelica.Functions.and4(CRMLtoModelica.Functions.cvBooleanToBoolean4(T <= 30), CRMLtoModelica.Functions.cvBooleanToBoolean4(T >= 16));
CRMLtoModelica.Types.Boolean4 R1_T = 'ensure49'.out;
CRMLtoModelica.Types.Boolean4 R2_T = 'check at end51'.out;
CRMLtoModelica.Types.Boolean4 R_T = CRMLtoModelica.Functions.and4(R2_T, R1_T);
model Req_speed
Real v;
CRMLtoModelica.Types.Boolean4 v_too_high = CRMLtoModelica.Functions.cvBooleanToBoolean4(v > 6.0);
CRMLtoModelica.Types.CRMLClock v_too_high_clock = c54;
CRMLtoModelica.Types.Boolean4 R_v;
end Req_speed; 
Real v1;
Real v2;
Req_speed Req_speed1(v = v1);
Req_speed Req_speed2(v = v2);
CRMLtoModelica.Types.Boolean4 R_speed_all = CRMLtoModelica.Functions.and4(Req_speed2.R_v, Req_speed1.R_v);
model Req_flow
CRMLtoModelica.Types.Boolean4 pump_in_servReal;
Real floww;
CRMLtoModelica.Types.Boolean4 f_over_fmin = CRMLtoModelica.Functions.cvBooleanToBoolean4(floww >= 700);
CRMLtoModelica.Types.Boolean4 R_f = 'ensure55'.out;
end Req_flow; 
CRMLtoModelica.Types.Boolean4 pump_in_service1;
CRMLtoModelica.Types.Boolean4 pump_in_service2;
CRMLtoModelica.Types.Boolean4 pump_in_service3;
Real flow1;
Real flow2;
Real flow3;
Req_flow Req_flow1(pump_in_service = pump_in_service1, floww = flow1);
Req_flow Req_flow2(pump_in_service = pump_in_service2, floww = flow2);
Req_flow Req_flow3(pump_in_service = pump_in_service3, floww = flow3);
CRMLtoModelica.Types.Boolean4 R_flow_all = CRMLtoModelica.Functions.and4(Req_flow3.R_f, CRMLtoModelica.Functions.and4(Req_flow2.R_f, Req_flow1.R_f));
'during' 'during49'(b=normal_operation);
'ensure' 'ensure49'(b=T_in_range, P='during49'.out);
CRMLtoModelica.Types.CRMLClock c51(b=CRMLtoModelica.Functions.not4( R1_T));
CRMLtoModelica.Types.CRMLClock_build c51_init(clock =c51);
'fromfor' 'fromfor51'(d=60.0, ev=c51);
'check at end' 'check at end51'(b=T_in_range, P='fromfor51'.out);
CRMLtoModelica.Types.CRMLClock c54(b=v_too_high);
CRMLtoModelica.Types.CRMLClock_build c54_init(clock =c54);
'during' 'during55'(b=pump_in_service);
'ensure' 'ensure55'(b=f_over_fmin, P='during55'.out);
end ics_reqs;
