// One stroke weight (1.5), one 20px grid, drawn for this app.
const base = { width: 20, height: 20, viewBox: "0 0 20 20", fill: "none", stroke: "currentColor", strokeWidth: 1.5, strokeLinecap: "round", strokeLinejoin: "round", "aria-hidden": true };

export const UploadIcon = (p) => (<svg {...base} {...p}><path d="M10 13V3.5M6.5 7 10 3.5 13.5 7M3.5 13v2.5a1 1 0 0 0 1 1h11a1 1 0 0 0 1-1V13" /></svg>);
export const FileIcon = (p) => (<svg {...base} {...p}><path d="M5 2.5h6l4 4v10a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1v-13a1 1 0 0 1 1-1ZM11 2.5v4h4" /></svg>);
export const CheckIcon = (p) => (<svg {...base} {...p}><path d="m4.5 10.5 3.5 3.5 7.5-8" /></svg>);
export const AlertIcon = (p) => (<svg {...base} {...p}><path d="M10 3 2.5 16h15L10 3ZM10 8v3.5M10 14v.01" /></svg>);
export const ArrowUpIcon = (p) => (<svg {...base} {...p}><path d="M10 16V4M5 9l5-5 5 5" /></svg>);
export const ArrowDownIcon = (p) => (<svg {...base} {...p}><path d="M10 4v12M5 11l5 5 5-5" /></svg>);
export const SearchIcon = (p) => (<svg {...base} {...p}><circle cx="9" cy="9" r="5" /><path d="m13 13 3.5 3.5" /></svg>);
export const CloseIcon = (p) => (<svg {...base} {...p}><path d="m5 5 10 10M15 5 5 15" /></svg>);
export const BackIcon = (p) => (<svg {...base} {...p}><path d="M12 4 6 10l6 6" /></svg>);
