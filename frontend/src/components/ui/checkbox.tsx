import * as React from "react"
import { Check } from "lucide-react"
import { cn } from "@/lib/utils"

interface CheckboxProps {
  id?: string
  checked?: boolean
  defaultChecked?: boolean
  disabled?: boolean
  className?: string
  onCheckedChange?: (checked: boolean) => void
}

function Checkbox({ id, checked, defaultChecked, disabled, className, onCheckedChange }: CheckboxProps) {
  const [internal, setInternal] = React.useState(defaultChecked ?? false)
  const isControlled = checked !== undefined
  const isChecked = isControlled ? checked : internal

  function handleClick() {
    if (disabled) return
    const next = !isChecked
    if (!isControlled) setInternal(next)
    onCheckedChange?.(next)
  }

  return (
    <button
      id={id}
      type="button"
      role="checkbox"
      aria-checked={isChecked}
      disabled={disabled}
      onClick={handleClick}
      className={cn(
        "inline-flex size-4 shrink-0 items-center justify-center rounded-sm border border-input transition-colors outline-none",
        "focus-visible:ring-[3px] focus-visible:ring-ring/50 focus-visible:border-ring",
        "disabled:cursor-not-allowed disabled:opacity-50",
        isChecked && "bg-primary border-primary text-primary-foreground",
        className,
      )}
    >
      {isChecked && <Check className="size-3" />}
    </button>
  )
}

export { Checkbox }
