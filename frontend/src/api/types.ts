export interface SourceRef {
  sourceId: string;
  title: string;
  url: string;
  gameVersion: string;
  revision: string | null;
  capturedAt: string;
  sourceType?: "WIKI" | "OFFICIAL_CHANGELOG" | "PLAYER_GUIDE";
  language?: string;
  sourceFamily?: string;
  author?: string | null;
  publishedAt?: string | null;
  applicableVersions?: string[];
}
export interface ToolResult<T> {
  status: string;
  data: T;
  sources: SourceRef[];
  assumptions: string[];
  errorCode: string | null;
}
export interface GameDate {
  year: number;
  season: "spring" | "summer" | "fall" | "winter";
  day: number;
}
export interface CropProjection {
  cropId: string;
  name: string;
  harvests: { date: GameDate; quantity: number; revenue: number }[];
  totalCost: number;
  totalRevenue: number;
  netProfit: number;
  feasible: boolean;
  reason: string | null;
}
export interface MaterialExpansion {
  targetId: string;
  quantity: number;
  materials: {
    id: string;
    name: string;
    totalRequired: number;
    ownedQuantity: number;
    missingQuantity: number;
    acquisition: string[];
  }[];
  prerequisites: string[];
  gaps: string[];
  sources: SourceRef[];
}
export interface FishMatches {
  fish: {
    id: string;
    name: string;
    habitats: {
      seasons: string[];
      weather: string[];
      locations: string[];
      startTime: number;
      endTime: number;
    }[];
  }[];
}
export interface GiftMatches {
  preferences: {
    villagerId: string;
    villagerName: string;
    itemId: string;
    itemName: string;
    level: string;
    rule: string;
  }[];
}
export interface BundleMissing {
  bundleId: string;
  bundleName: string;
  requiredSlots: number;
  completedSlots: number;
  remainingSlots: number;
  goldRequired: number;
  goldMissing: number;
  options: {
    slotId?: string;
    itemId: string;
    name: string;
    quantity: number;
    quality: string;
    submitted: number;
    usableOwned: number;
    missing: number;
  }[];
}
export interface GameEntity {
  id: string;
  name: string;
  type: string;
  attributes: Record<string, unknown>;
}
export interface ResultBundle {
  comparison?: ToolResult<CropProjection[]>;
  material_list?: ToolResult<MaterialExpansion>;
  fish_matches?: ToolResult<FishMatches>;
  gift_preferences?: ToolResult<GiftMatches>;
  bundle_missing?: ToolResult<BundleMissing>;
}
export interface ToolExecution {
  name: string;
  args: Record<string, unknown>;
  result: ToolResult<unknown>;
}
export interface Message {
  id: number;
  role: "user" | "assistant";
  content: string;
  toolResults: ToolExecution[];
  sources: SourceRef[];
  createdAt?: string;
}
export interface Session {
  id: string;
  title: string;
  createdAt: string;
  updatedAt: string;
}
export interface Turn {
  sessionId: string;
  messageId: number;
  answer: string;
  toolResults: ToolExecution[];
  sources: SourceRef[];
}
export interface Entity {
  id: string;
  name: string;
  attributes: Record<string, any>;
}
export interface Options {
  tool: string;
  options: Record<string, any>;
}
