#!/usr/bin/env python3
"""pip install osmium shapely; python tools/build_snapshot.py hong-kong.osm.pbf
Data: © OpenStreetMap contributors, ODbL 1.0. Never merges proprietary POI lists.
"""
import json, sys
from pathlib import Path
import osmium
from shapely.geometry import shape

source = Path(sys.argv[1])
assets = Path(__file__).resolve().parents[1] / 'app/src/main/assets'
factory = osmium.geom.GeoJSONFactory()
class Extract(osmium.SimpleHandler):
    def __init__(self):
        super().__init__(); self.cafes = {}; self.districts = []
    def node(self, n):
        if n.tags.get('amenity') == 'cafe' and n.location.valid():
            self.cafes[f'node/{n.id}'] = dict(type='node', id=n.id, lat=n.location.lat, lon=n.location.lon, tags=dict(n.tags))
    def way(self, w):
        if w.tags.get('amenity') == 'cafe':
            coords = [(n.lat,n.lon) for n in w.nodes if n.location.valid()]
            if coords:
                self.cafes[f'way/{w.id}'] = dict(type='way',id=w.id,center=dict(lat=sum(p[0] for p in coords)/len(coords),lon=sum(p[1] for p in coords)/len(coords)),tags=dict(w.tags))
    def area(self, a):
        if a.tags.get('boundary') == 'administrative' and a.tags.get('admin_level') == '6':
            geom = json.loads(factory.create_multipolygon(a))
            self.districts.append(dict(name=a.tags.get('name:zh') or a.tags.get('name'),english=a.tags.get('name:en',''),geometry=geom))
        elif not a.from_way() and a.tags.get('amenity') == 'cafe':
            p = shape(json.loads(factory.create_multipolygon(a))).representative_point()
            self.cafes[f'relation/{a.orig_id()}'] = dict(type='relation',id=a.orig_id(),center=dict(lat=p.y,lon=p.x),tags=dict(a.tags))
handler=Extract();handler.apply_file(str(source),locations=True,idx='flex_mem')
# District geometry also excludes any bordering non-HK features in the extract.
districts=[d for d in handler.districts if d['name'] in ['中西區','灣仔區','東區','南區','油尖旺區','深水埗區','九龍城區','黃大仙區','觀塘區','葵青區','荃灣區','屯門區','元朗區','北區','大埔區','沙田區','西貢區','離島區']]
shapes=[shape(d['geometry']) for d in districts]
cafes=[v for v in handler.cafes.values() if any(poly.covers(__import__('shapely').geometry.Point((v.get('center') or v)['lon'],(v.get('center') or v)['lat'])) for poly in shapes)]
reader=osmium.io.Reader(str(source));header=reader.header();timestamp=header.get('osmosis_replication_timestamp');reader.close()
output=dict(version=0.6,generator='HK Coffee / Geofabrik extract',osm3s=dict(timestamp_osm_base=timestamp,copyright='© OpenStreetMap contributors — ODbL 1.0'),elements=cafes)
assets.mkdir(parents=True,exist_ok=True)
(assets/'cafes.json').write_text(json.dumps(output,ensure_ascii=False,separators=(',',':')))
(assets/'districts.json').write_text(json.dumps(districts,ensure_ascii=False,separators=(',',':')))
print('snapshot timestamp:',timestamp,'cafes:',len(cafes),'districts:',len(districts))
print([(d['name'],d['english']) for d in districts])
assert len(districts)==18, 'All 18 district boundaries are required'
assert len(cafes)>100, 'Unexpectedly small data extract'
