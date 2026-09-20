import Svg, { Circle, Ellipse, Path } from 'react-native-svg';
import { mascotColors } from '@/src/theme';

interface Props {
  size?: number;
}

// "Thyme" — a cutesey plant creature, not just a potted plant with a face: the leafy blob IS
// the character (face lives on it, plus two little waving leaf-arms), standing in a small pot
// like a pair of feet. Pure SVG so it scales crisply at any size with zero bundle weight.
export function Mascot({ size = 64 }: Props) {
  return (
    <Svg width={size} height={size} viewBox="0 0 100 100">
      {/* pot feet */}
      <Path d="M32 78 L68 78 L64 94 Q50 98 36 94 Z" fill={mascotColors.pot} />
      <Path d="M30 78 L70 78 L70 84 L30 84 Z" fill={mascotColors.potRim} />

      {/* little waving leaf arms */}
      <Path d="M19 54 Q6 49 9 37 Q21 41 24 54 Z" fill={mascotColors.leaf} />
      <Path d="M81 54 Q94 49 91 37 Q79 41 76 54 Z" fill={mascotColors.leaf} />

      {/* leafy body/head */}
      <Ellipse cx={50} cy={46} rx={29} ry={27} fill={mascotColors.leaf} />
      <Ellipse cx={29} cy={39} rx={15} ry={13} fill={mascotColors.leafLight} />
      <Ellipse cx={71} cy={39} rx={15} ry={13} fill={mascotColors.leafDark} />
      <Ellipse cx={50} cy={23} rx={14} ry={12} fill={mascotColors.leafLight} />

      {/* face */}
      <Circle cx={40} cy={48} r={4.6} fill={mascotColors.face} />
      <Circle cx={60} cy={48} r={4.6} fill={mascotColors.face} />
      <Circle cx={41.6} cy={46.4} r={1.4} fill="#FFFFFF" />
      <Circle cx={61.6} cy={46.4} r={1.4} fill="#FFFFFF" />
      <Ellipse cx={33} cy={55} rx={5} ry={3} fill={mascotColors.blush} opacity={0.75} />
      <Ellipse cx={67} cy={55} rx={5} ry={3} fill={mascotColors.blush} opacity={0.75} />
      <Path
        d="M43 57 Q50 63.5 57 57"
        stroke={mascotColors.face}
        strokeWidth={2.6}
        strokeLinecap="round"
        fill="none"
      />
    </Svg>
  );
}
