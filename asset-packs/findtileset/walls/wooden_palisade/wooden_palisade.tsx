<?xml version="1.0" encoding="UTF-8"?>
<tileset version="1.10" name="wooden_palisade" tilewidth="32" tileheight="32" tilecount="25" columns="5">
 <image source="wooden_palisade_sheet.png" width="160" height="160"/>
 <tile id="1">
   <objectgroup draworder="index">
    <object id="1" type="collision" x="16" y="16" width="16" height="16"/>
   </objectgroup>
 </tile>
 <tile id="2">
   <objectgroup draworder="index">
    <object id="1" type="collision" x="0" y="16" width="32" height="16"/>
   </objectgroup>
 </tile>
 <tile id="3">
   <objectgroup draworder="index">
    <object id="1" type="collision" x="0" y="16" width="16" height="16"/>
   </objectgroup>
 </tile>
 <tile id="4">
   <objectgroup draworder="index">
    <object id="1" type="collision" x="16" y="0" width="16" height="16"/>
    <object id="2" type="collision" x="16" y="16" width="16" height="16"/>
   </objectgroup>
 </tile>
 <tile id="5">
   <objectgroup draworder="index">
    <object id="1" type="collision" x="0" y="0" width="16" height="16"/>
    <object id="2" type="collision" x="0" y="16" width="16" height="16"/>
   </objectgroup>
 </tile>
 <tile id="6">
   <objectgroup draworder="index">
    <object id="1" type="collision" x="16" y="0" width="16" height="16"/>
    <object id="2" type="collision" x="16" y="16" width="16" height="16"/>
   </objectgroup>
 </tile>
 <tile id="7">
   <objectgroup draworder="index">
    <object id="1" type="collision" x="0" y="0" width="16" height="16"/>
    <object id="2" type="collision" x="0" y="16" width="32" height="16"/>
   </objectgroup>
 </tile>
 <tile id="8">
   <objectgroup draworder="index">
    <object id="1" type="collision" x="16" y="0" width="16" height="16"/>
    <object id="2" type="collision" x="0" y="16" width="32" height="16"/>
   </objectgroup>
 </tile>
 <tile id="9">
   <objectgroup draworder="index">
    <object id="1" type="collision" x="0" y="0" width="16" height="16"/>
    <object id="2" type="collision" x="0" y="16" width="16" height="16"/>
   </objectgroup>
 </tile>
 <tile id="10">
   <objectgroup draworder="index">
    <object id="1" type="collision" x="0" y="0" width="32" height="32"/>
   </objectgroup>
 </tile>
 <tile id="11">
   <objectgroup draworder="index">
    <object id="1" type="collision" x="0" y="0" width="32" height="32"/>
   </objectgroup>
 </tile>
 <tile id="12">
   <objectgroup draworder="index">
    <object id="1" type="collision" x="0" y="0" width="32" height="32"/>
   </objectgroup>
 </tile>
 <tile id="13">
   <objectgroup draworder="index">
    <object id="1" type="collision" x="0" y="0" width="16" height="16"/>
    <object id="2" type="collision" x="0" y="16" width="16" height="16"/>
   </objectgroup>
 </tile>
 <tile id="14">
   <objectgroup draworder="index">
    <object id="1" type="collision" x="0" y="0" width="32" height="16"/>
    <object id="2" type="collision" x="0" y="16" width="16" height="16"/>
   </objectgroup>
 </tile>
 <tile id="15">
   <objectgroup draworder="index">
    <object id="1" type="collision" x="0" y="0" width="32" height="16"/>
   </objectgroup>
 </tile>
 <tile id="16">
   <objectgroup draworder="index">
    <object id="1" type="collision" x="0" y="0" width="16" height="16"/>
   </objectgroup>
 </tile>
 <tile id="17">
   <objectgroup draworder="index">
    <object id="1" type="collision" x="16" y="0" width="16" height="16"/>
    <object id="2" type="collision" x="16" y="16" width="16" height="16"/>
   </objectgroup>
 </tile>
 <tile id="18">
   <objectgroup draworder="index">
    <object id="1" type="collision" x="0" y="0" width="32" height="32"/>
   </objectgroup>
 </tile>
 <tile id="19">
   <objectgroup draworder="index">
    <object id="1" type="collision" x="0" y="0" width="16" height="16"/>
    <object id="2" type="collision" x="0" y="16" width="32" height="16"/>
   </objectgroup>
 </tile>
 <tile id="20">
   <objectgroup draworder="index">
    <object id="1" type="collision" x="16" y="0" width="16" height="16"/>
   </objectgroup>
 </tile>
 <tile id="21">
   <objectgroup draworder="index">
    <object id="1" type="collision" x="0" y="0" width="32" height="16"/>
    <object id="2" type="collision" x="16" y="16" width="16" height="16"/>
   </objectgroup>
 </tile>
 <tile id="22">
   <objectgroup draworder="index">
    <object id="1" type="collision" x="0" y="0" width="32" height="16"/>
    <object id="2" type="collision" x="16" y="16" width="16" height="16"/>
   </objectgroup>
 </tile>
 <tile id="23">
   <objectgroup draworder="index">
    <object id="1" type="collision" x="16" y="0" width="16" height="16"/>
    <object id="2" type="collision" x="0" y="16" width="32" height="16"/>
   </objectgroup>
 </tile>
 <tile id="24">
   <objectgroup draworder="index">
    <object id="1" type="collision" x="0" y="0" width="32" height="16"/>
    <object id="2" type="collision" x="0" y="16" width="16" height="16"/>
   </objectgroup>
 </tile>
 <wangsets>
  <wangset name="wooden_palisade" type="corner" tile="-1">
   <wangcolor name="ground" color="#8a6a3c" tile="-1" probability="1"/>
   <wangcolor name="wall top" color="#c8c8c8" tile="-1" probability="1"/>
   <wangcolor name="wall face" color="#404048" tile="-1" probability="1"/>
   <wangtile tileid="0" wangid="0,1,0,1,0,1,0,1"/>
   <wangtile tileid="1" wangid="0,1,0,2,0,1,0,1"/>
   <wangtile tileid="2" wangid="0,1,0,2,0,2,0,1"/>
   <wangtile tileid="3" wangid="0,1,0,1,0,2,0,1"/>
   <wangtile tileid="4" wangid="0,2,0,2,0,1,0,1"/>
   <wangtile tileid="5" wangid="0,1,0,1,0,2,0,2"/>
   <wangtile tileid="6" wangid="0,2,0,2,0,1,0,1"/>
   <wangtile tileid="7" wangid="0,1,0,2,0,2,0,2"/>
   <wangtile tileid="8" wangid="0,2,0,2,0,2,0,1"/>
   <wangtile tileid="9" wangid="0,1,0,1,0,2,0,2"/>
   <wangtile tileid="10" wangid="0,2,0,2,0,2,0,2"/>
   <wangtile tileid="11" wangid="0,2,0,3,0,2,0,2"/>
   <wangtile tileid="12" wangid="0,2,0,3,0,3,0,2"/>
   <wangtile tileid="13" wangid="0,1,0,1,0,3,0,2"/>
   <wangtile tileid="14" wangid="0,3,0,1,0,2,0,2"/>
   <wangtile tileid="15" wangid="0,3,0,1,0,1,0,3"/>
   <wangtile tileid="16" wangid="0,1,0,1,0,1,0,3"/>
   <wangtile tileid="17" wangid="0,2,0,3,0,1,0,1"/>
   <wangtile tileid="18" wangid="0,2,0,2,0,3,0,2"/>
   <wangtile tileid="19" wangid="0,1,0,2,0,3,0,2"/>
   <wangtile tileid="20" wangid="0,3,0,1,0,1,0,1"/>
   <wangtile tileid="21" wangid="0,2,0,2,0,1,0,3"/>
   <wangtile tileid="22" wangid="0,2,0,2,0,1,0,3"/>
   <wangtile tileid="23" wangid="0,2,0,3,0,2,0,1"/>
   <wangtile tileid="24" wangid="0,3,0,1,0,2,0,2"/>
  </wangset>
 </wangsets>
</tileset>
