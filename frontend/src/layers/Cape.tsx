import { ColorScale, Color } from "../ColorScale";
import {type ForecastMetadata} from '../data/ForecastMetadata';
import {colorScaleEl, Layer, ReactiveComponents, summarizerFromLocationDetails} from './Layer';
import {useI18n, usingMessages} from "../i18n";
import {type Zone} from "../data/Model";

export const capeColorScale = new ColorScale([
  [0, new Color(0xf0, 0xf0, 0xff, 1)],
  [500, new Color(0x96, 0xc8, 0xff, 1)],
  [1000, new Color(0x64, 0xff, 0x96, 1)],
  [1500, new Color(0xff, 0xff, 0x64, 1)],
  [2000, new Color(0xff, 0x96, 0x32, 1)],
  [3000, new Color(0xc8, 0x32, 0x32, 1)]
]);

export const capeLayer: Layer = {
  key: 'cape',
  name: usingMessages(m => m.layerCape()),
  title: usingMessages(m => m.layerCapeLegend()),
  dataPath: 'cape',
  reactiveComponents(props: {
    forecastMetadata: ForecastMetadata,
    zone: Zone,
    hourOffset: number
  }): ReactiveComponents {

    const { m } = useI18n();

    const summarizer = summarizerFromLocationDetails(props, detailedForecast => [
      [() => m().summaryCape(), <span>{ detailedForecast.cape || 0 } J/kg</span>]
    ]);

    return {
      summarizer,
      mapKey: colorScaleEl(capeColorScale, value => `${value} J/kg `),
      help: <p>
        { m().helpLayerCape() }
      </p>
    }
  }
};
